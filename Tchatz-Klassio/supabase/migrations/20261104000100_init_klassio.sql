-- =====================================================================
-- TCHATZ KLASSIO — Migration initiale (MVP V1)
-- Plateforme de partage de ressources pédagogiques
-- Une solution de Tchatz — Samson Adjaho
--
-- Contenu :
--   1. Extensions
--   2. Tables métier + contraintes + index
--   3. Fonctions utilitaires et de sécurité
--   4. RPC publiques (étudiant / enseignant / responsable)
--   5. Row Level Security
--   6. Stockage (bucket privé documents)
-- =====================================================================

create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------------
-- 1. TABLES
-- ---------------------------------------------------------------------

-- profiles : un profil par responsable (utilisateur Supabase Auth)
create table if not exists public.profiles (
    id          uuid primary key references auth.users (id) on delete cascade,
    full_name   text not null check (length(btrim(full_name)) between 2 and 120),
    email       text not null,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

-- classrooms : espaces de classe
create table if not exists public.classrooms (
    id                uuid primary key default gen_random_uuid(),
    owner_id          uuid not null references auth.users (id) on delete cascade,
    institution_name  text not null check (length(btrim(institution_name)) between 2 and 160),
    class_name        text not null check (length(btrim(class_name)) between 2 and 120),
    field_of_study    text not null check (length(btrim(field_of_study)) between 2 and 160),
    level             text not null default 'Licence'
                      check (level in ('Licence', 'Master', 'BTS', 'DUT', 'Doctorat', 'Autre')),
    study_year        text not null check (length(btrim(study_year)) between 1 and 60),
    academic_year     text not null check (academic_year ~ '^[0-9]{4}-[0-9]{4}$'),
    join_code         text not null unique check (join_code ~ '^[A-Z0-9]{8}$'),
    is_archived       boolean not null default false,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

comment on column public.classrooms.level is 'Niveau d''étude (Licence, Master, ...)';
comment on column public.classrooms.study_year is 'Année dans le niveau : 1, 2, 3...';

-- subjects : matières d'une classe
create table if not exists public.subjects (
    id            uuid primary key default gen_random_uuid(),
    classroom_id  uuid not null references public.classrooms (id) on delete cascade,
    name          text not null check (length(btrim(name)) between 2 and 120),
    description   text check (description is null or length(description) <= 1000),
    position      integer not null default 0,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    archived_at   timestamptz
);

create unique index if not exists subjects_unique_active_name
    on public.subjects (classroom_id, lower(btrim(name)))
    where archived_at is null;

create index if not exists subjects_classroom_idx on public.subjects (classroom_id);

-- publication_links : jetons de publication par matière (hachés)
create table if not exists public.publication_links (
    id          uuid primary key default gen_random_uuid(),
    subject_id  uuid not null references public.subjects (id) on delete cascade,
    token_hash  bytea not null,
    token_hint  text,
    status      text not null default 'active' check (status in ('active', 'revoked')),
    created_at  timestamptz not null default now(),
    revoked_at  timestamptz
);

-- un seul lien actif par matière : « Limiter chaque lien à une seule matière »
create unique index if not exists publication_links_one_active_per_subject
    on public.publication_links (subject_id)
    where status = 'active';
create unique index if not exists publication_links_token_hash_key
    on public.publication_links (token_hash);

-- documents : ressources pédagogiques publiées
create table if not exists public.documents (
    id                    uuid primary key default gen_random_uuid(),
    subject_id            uuid not null references public.subjects (id) on delete cascade,
    title                 text not null check (length(btrim(title)) between 2 and 160),
    description           text check (description is null or length(description) <= 1200),
    file_name             text not null,
    file_path             text not null unique,
    file_type             text not null,
    file_size             bigint not null check (file_size > 0 and file_size <= 52428800),
    teacher_display_name  text not null check (length(btrim(teacher_display_name)) between 2 and 80),
    publication_link_id   uuid references public.publication_links (id) on delete set null,
    created_at            timestamptz not null default now()
);

create index if not exists documents_subject_created_idx
    on public.documents (subject_id, created_at desc);
create index if not exists documents_created_idx
    on public.documents (created_at desc);

-- student_sessions : sessions étudiantes sans compte (jeton opaque haché)
-- Une session porte sur une seule classe. Retrouver ses classes = re-saisir son code.
create table if not exists public.student_sessions (
    id             uuid primary key default gen_random_uuid(),
    classroom_id   uuid not null references public.classrooms (id) on delete cascade,
    token_hash     bytea not null unique,
    label          text,
    created_at     timestamptz not null default now(),
    last_seen_at   timestamptz not null default now(),
    expires_at     timestamptz not null default now() + interval '365 days'
);

create index if not exists student_sessions_classroom_idx
    on public.student_sessions (classroom_id);

-- student_devices : appareils rattachés à une classe (sans identifiant matériel)
create table if not exists public.student_devices (
    id                       uuid primary key default gen_random_uuid(),
    classroom_id             uuid not null references public.classrooms (id) on delete cascade,
    student_session_id       uuid not null references public.student_sessions (id) on delete cascade,
    device_identifier_hash   bytea not null,
    label                    text,
    platform                 text not null default 'web' check (platform in ('android', 'web')),
    created_at               timestamptz not null default now(),
    last_seen_at             timestamptz not null default now(),
    unique (classroom_id, device_identifier_hash)
);

create index if not exists student_devices_session_idx
    on public.student_devices (student_session_id);

-- notification_preferences : opt-in par appareil et par classe
create table if not exists public.notification_preferences (
    id                     uuid primary key default gen_random_uuid(),
    student_device_id      uuid not null references public.student_devices (id) on delete cascade,
    classroom_id           uuid not null references public.classrooms (id) on delete cascade,
    student_session_id     uuid not null references public.student_sessions (id) on delete cascade,
    enabled                boolean not null default true,
    provider               text not null default 'none' check (provider in ('none', 'webpush')),
    push_subscription      jsonb,
    created_at             timestamptz not null default now(),
    updated_at             timestamptz not null default now(),
    unique (student_device_id, classroom_id)
);

-- publication_rate_limits : anti-spam par lien de publication (fenêtre glissante)
create table if not exists public.publication_rate_limits (
    id                    uuid primary key default gen_random_uuid(),
    publication_link_id   uuid not null references public.publication_links (id) on delete cascade,
    ip_hash               bytea,
    documented_at         timestamptz not null default now()
);

create index if not exists publication_rate_limits_idx
    on public.publication_rate_limits (publication_link_id, documented_at desc);

-- access_logs : journal des codes erronés (limitation des tentatives)
create table if not exists public.access_logs (
    id           bigint generated always as identity primary key,
    ip_hash      bytea,
    outcome      text not null check (outcome in ('invalid_code', 'classroom_not_found')),
    created_at   timestamptz not null default now()
);

create index if not exists access_logs_created_idx on public.access_logs (created_at desc);

-- platform_settings : limites configurables par l'administrateur technique
create table if not exists public.platform_settings (
    key         text primary key,
    value       jsonb not null,
    updated_at  timestamptz not null default now()
);

insert into public.platform_settings (key, value)
values
    ('max_upload_bytes', '10485760'::jsonb),
    ('max_publications_per_link_per_hour', '10'::jsonb),
    ('join_code_length', '8'::jsonb)
on conflict (key) do nothing;

-- ---------------------------------------------------------------------
-- 2. TRIGGERS UTILITAIRES
-- ---------------------------------------------------------------------

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch before update on public.profiles
    for each row execute function public.set_updated_at();

drop trigger if exists classrooms_touch on public.classrooms;
create trigger classrooms_touch before update on public.classrooms
    for each row execute function public.set_updated_at();

drop trigger if exists subjects_touch on public.subjects;
create trigger subjects_touch before update on public.subjects
    for each row execute function public.set_updated_at();

-- profil créé automatiquement à l'inscription
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, auth
as $$
begin
    insert into public.profiles (id, full_name, email)
    values (
        new.id,
        coalesce(nullif(btrim(new.raw_user_meta_data ->> 'full_name'), ''), split_part(new.email, '@', 1)),
        coalesce(new.email, '')
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------
-- 3. FONCTIONS UTILITAIRES
-- ---------------------------------------------------------------------

create or replace function public.sha256_hex(p_value text)
returns text
language sql
immutable
as $$
    select encode(digest(p_value, 'sha256'), 'hex');
$$;

create or replace function public.sha256_bytes(p_value text)
returns bytea
language sql
immutable
as $$
    select digest(p_value, 'sha256');
$$;

-- Raccourci base32 (sans caractères ambigus 0/O/1/I) pour les codes de classe
create or replace function public.random_join_code(p_length integer default 8)
returns text
language plpgsql
volatile
as $$
declare
    alphabet constant text := '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
    result text := '';
    i integer;
begin
    for i in 1..greatest(6, least(12, p_length)) loop
        result := result || substr(alphabet, 1 + floor(random() * length(alphabet))::integer, 1);
    end loop;
    return result;
end;
$$;

create or replace function public.config_text(p_key text)
returns text
language sql
stable
security definer
set search_path = public
as $$
    select value #>> '{}' from public.platform_settings where key = p_key;
$$;

create or replace function public.config_int(p_key text, p_default bigint)
returns bigint
language sql
stable
security definer
set search_path = public
as $$
    select coalesce((value #>> '{}')::bigint, p_default)
    from public.platform_settings
    where key = p_key;
$$;

revoke all on function public.config_text(text) from anon, authenticated;
revoke all on function public.config_int(text, bigint) from anon, authenticated;

-- ---------------------------------------------------------------------
-- 4. RPC — ESPACE ÉTUDIANT (sans compte)
-- ---------------------------------------------------------------------

-- Rejoindre une classe avec un code. Renvoie un jeton de session opaque (1 an).
create or replace function public.api_join_classroom(
    p_join_code text,
    p_label text default null,
    p_platform text default 'web'
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_classroom public.classrooms;
    v_token text;
    v_session public.student_sessions;
begin
    if p_join_code is null or btrim(p_join_code) = '' then
        raise exception 'CODE_REQUIRED' using errcode = 'P0001';
    end if;

    select * into v_classroom
    from public.classrooms
    where join_code = upper(regexp_replace(btrim(p_join_code), '\s', '', 'g'))
      and is_archived = false;

    if not found then
        insert into public.access_logs (outcome) values ('invalid_code');
        raise exception 'CLASSROOM_NOT_FOUND' using errcode = 'P0001';
    end if;

    -- Jeton opaque : 32 octets aléatoires, base64 url-safe
    v_token := replace(replace(encode(extensions.gen_random_bytes(32), 'base64'), '+', '-'), '/', '_');

    insert into public.student_sessions (classroom_id, token_hash, label)
    values (v_classroom.id, public.sha256_bytes(v_token), left(coalesce(p_label, ''), 80))
    returning * into v_session;

    return jsonb_build_object(
        'session_token', v_token,
        'expires_at', v_session.expires_at,
        'classroom', jsonb_build_object(
            'id', v_classroom.id,
            'institution_name', v_classroom.institution_name,
            'class_name', v_classroom.class_name,
            'field_of_study', v_classroom.field_of_study,
            'level', v_classroom.level,
            'study_year', v_classroom.study_year,
            'academic_year', v_classroom.academic_year,
            'join_code', v_classroom.join_code,
            'owner_name', null
        )
    );
end;
$$;

create or replace function public.student_session_classroom(p_token text)
returns public.classrooms
language sql
stable
security definer
set search_path = public
as $$
    select c.*
    from public.student_sessions s
    join public.classrooms c on c.id = s.classroom_id
    where s.token_hash = public.sha256_bytes(p_token)
      and s.expires_at > now()
      and c.is_archived = false;
$$;

-- Détail complet d'une classe étudiante (matières + document counts)
create or replace function public.api_student_classroom(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_c public.classrooms;
begin
    select * into v_c from public.student_session_classroom(p_token);
    if not found then
        raise exception 'SESSION_INVALID' using errcode = 'P0001';
    end if;

    update public.student_sessions set last_seen_at = now()
    where token_hash = public.sha256_bytes(p_token);

    return jsonb_build_object(
        'classroom', jsonb_build_object(
            'id', v_c.id,
            'institution_name', v_c.institution_name,
            'class_name', v_c.class_name,
            'field_of_study', v_c.field_of_study,
            'level', v_c.level,
            'study_year', v_c.study_year,
            'academic_year', v_c.academic_year,
            'join_code', v_c.join_code
        ),
        'subjects', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', s.id,
                'name', s.name,
                'description', s.description,
                'position', s.position,
                'document_count', (select count(*) from public.documents d where d.subject_id = s.id),
                'last_document_at', (select max(d.created_at) from public.documents d where d.subject_id = s.id)
            ) order by s.position, s.name)
            from public.subjects s
            where s.classroom_id = v_c.id and s.archived_at is null
        ), '[]'::jsonb),
        'document_count', (select count(*) from public.documents d
                           join public.subjects s on s.id = d.subject_id
                           where s.classroom_id = v_c.id)
    );
end;
$$;

-- Liste paginée et recherchable des documents d'une classe
create or replace function public.api_student_documents(
    p_token text,
    p_subject_id uuid default null,
    p_search text default null,
    p_limit integer default 30,
    p_offset integer default 0
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_c public.classrooms;
    v_limit integer := greatest(1, least(coalesce(p_limit, 30), 100));
    v_offset integer := greatest(0, coalesce(p_offset, 0));
    v_search text := nullif(btrim(coalesce(p_search, '')), '');
begin
    select * into v_c from public.student_session_classroom(p_token);
    if not found then
        raise exception 'SESSION_INVALID' using errcode = 'P0001';
    end if;

    if p_subject_id is not null and not exists (
        select 1 from public.subjects s
        where s.id = p_subject_id and s.classroom_id = v_c.id
    ) then
        raise exception 'SUBJECT_NOT_IN_CLASSROOM' using errcode = 'P0001';
    end if;

    return coalesce((
        select jsonb_agg(row_to_json(t)::jsonb)
        from (
            select d.id, d.subject_id, s.name as subject_name, d.title, d.description,
                   d.file_name, d.file_type, d.file_size, d.teacher_display_name, d.created_at
            from public.documents d
            join public.subjects s on s.id = d.subject_id
            where s.classroom_id = v_c.id
              and s.archived_at is null
              and (p_subject_id is null or d.subject_id = p_subject_id)
              and (v_search is null or d.title ilike '%' || v_search || '%'
                   or d.file_name ilike '%' || v_search || '%')
            order by d.created_at desc
            limit v_limit offset v_offset
        ) t
    ), '[]'::jsonb);
end;
$$;

-- URL signée temporaire pour consulter / télécharger un document protégé
create or replace function public.api_student_document_link(
    p_token text,
    p_document_id uuid,
    p_expires_seconds integer default 300
)
returns jsonb
language plpgsql
security definer
set search_path = public, storage
as $$
declare
    v_c public.classrooms;
    v_doc public.documents;
    v_expires integer := greatest(30, least(coalesce(p_expires_seconds, 300), 3600));
begin
    select * into v_c from public.student_session_classroom(p_token);
    if not found then
        raise exception 'SESSION_INVALID' using errcode = 'P0001';
    end if;

    select d.* into v_doc
    from public.documents d
    join public.subjects s on s.id = d.subject_id
    where d.id = p_document_id and s.classroom_id = v_c.id;

    if not found then
        raise exception 'DOCUMENT_NOT_FOUND' using errcode = 'P0001';
    end if;

    return jsonb_build_object(
        'document', jsonb_build_object(
            'id', v_doc.id,
            'title', v_doc.title,
            'file_name', v_doc.file_name,
            'file_type', v_doc.file_type,
            'file_size', v_doc.file_size,
            'teacher_display_name', v_doc.teacher_display_name,
            'created_at', v_doc.created_at
        ),
        'signed_url', (select url from storage.create_signed_url('documents', v_doc.file_path, v_expires)),
        'expires_in', v_expires
    );
end;
$$;

-- Enregistrement d'un appareil et des préférences de notification
create or replace function public.api_register_device(
    p_token text,
    p_device_identifier text,
    p_label text default null,
    p_platform text default 'web',
    p_notifications_enabled boolean default true,
    p_provider text default 'none',
    p_push_subscription jsonb default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_session public.student_sessions;
    v_device public.student_devices;
begin
    if p_device_identifier is null or length(btrim(p_device_identifier)) < 8 then
        raise exception 'DEVICE_IDENTIFIER_REQUIRED' using errcode = 'P0001';
    end if;

    select s.* into v_session
    from public.student_sessions s
    where s.token_hash = public.sha256_bytes(p_token) and s.expires_at > now();

    if not found then
        raise exception 'SESSION_INVALID' using errcode = 'P0001';
    end if;

    insert into public.student_devices (classroom_id, student_session_id, device_identifier_hash, label, platform)
    values (v_session.classroom_id, v_session.id, public.sha256_bytes(p_device_identifier),
            left(coalesce(p_label, ''), 80), case when p_platform = 'android' then 'android' else 'web' end)
    on conflict (classroom_id, device_identifier_hash)
    do update set last_seen_at = now(), label = excluded.label,
                  student_session_id = excluded.student_session_id
    returning * into v_device;

    insert into public.notification_preferences
        (student_device_id, classroom_id, student_session_id, enabled, provider, push_subscription)
    values (v_device.id, v_session.classroom_id, v_session.id,
            coalesce(p_notifications_enabled, true),
            case when p_provider = 'webpush' then 'webpush' else 'none' end,
            p_push_subscription)
    on conflict (student_device_id, classroom_id)
    do update set enabled = excluded.enabled,
                  provider = excluded.provider,
                  push_subscription = coalesce(excluded.push_subscription, public.notification_preferences.push_subscription),
                  student_session_id = excluded.student_session_id,
                  updated_at = now();

    return jsonb_build_object('device_id', v_device.id, 'notifications_enabled', coalesce(p_notifications_enabled, true));
end;
$$;

create or replace function public.api_set_notifications(
    p_token text,
    p_device_identifier text,
    p_enabled boolean,
    p_push_subscription jsonb default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_session public.student_sessions;
    v_device public.student_devices;
begin
    select s.* into v_session
    from public.student_sessions s
    where s.token_hash = public.sha256_bytes(p_token) and s.expires_at > now();
    if not found then
        raise exception 'SESSION_INVALID' using errcode = 'P0001';
    end if;

    select d.* into v_device
    from public.student_devices d
    where d.classroom_id = v_session.classroom_id
      and d.device_identifier_hash = public.sha256_bytes(p_device_identifier);
    if not found then
        raise exception 'DEVICE_NOT_FOUND' using errcode = 'P0001';
    end if;

    update public.notification_preferences
    set enabled = coalesce(p_enabled, enabled),
        provider = case when p_push_subscription is not null then 'webpush' else provider end,
        push_subscription = coalesce(p_push_subscription, push_subscription),
        updated_at = now()
    where student_device_id = v_device.id and classroom_id = v_session.classroom_id;

    return jsonb_build_object('ok', true);
end;
$$;

-- Nouveautés depuis la dernière synchronisation (notifications / rafraîchissement)
create or replace function public.api_student_updates(p_token text, p_since timestamptz)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_c public.classrooms;
begin
    select * into v_c from public.student_session_classroom(p_token);
    if not found then
        raise exception 'SESSION_INVALID' using errcode = 'P0001';
    end if;

    return coalesce((
        select jsonb_agg(row_to_json(t)::jsonb order by t.created_at desc)
        from (
            select d.id, d.subject_id, s.name as subject_name, d.title,
                   d.teacher_display_name, d.created_at
            from public.documents d
            join public.subjects s on s.id = d.subject_id
            where s.classroom_id = v_c.id
              and (p_since is null or d.created_at > p_since)
            order by d.created_at desc
            limit 50
        ) t
    ), '[]'::jsonb);
end;
$$;

-- ---------------------------------------------------------------------
-- 5. RPC — PAGE DE PUBLICATION ENSEIGNANT (sans compte, par jeton)
-- ---------------------------------------------------------------------

create or replace function public.publish_link_by_token(p_token text)
returns table (
    link_id uuid,
    subject_id uuid,
    subject_name text,
    subject_description text,
    classroom_id uuid,
    classroom_name text,
    institution_name text,
    field_of_study text,
    level text,
    study_year text,
    academic_year text,
    teacher_name_hint text,
    status text
)
language sql
stable
security definer
set search_path = public
as $$
    select pl.id, s.id, s.name, s.description, c.id, c.class_name, c.institution_name,
           c.field_of_study, c.level, c.study_year, c.academic_year,
           (select d.teacher_display_name
              from public.documents d
             where d.subject_id = s.id
             order by d.created_at desc limit 1),
           pl.status
    from public.publication_links pl
    join public.subjects s on s.id = pl.subject_id
    join public.classrooms c on c.id = s.classroom_id
    where pl.token_hash = public.sha256_bytes(p_token)
      and s.archived_at is null
      and c.is_archived = false;
$$;

-- Informations publiques minimales pour la page de publication (sans jeton visible)
create or replace function public.api_publish_context(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v public.publish_link_by_token%rowtype;
    v_max bigint;
    v_count integer;
begin
    select * into v from public.publish_link_by_token(p_token);
    if not found then
        raise exception 'LINK_INVALID' using errcode = 'P0001';
    end if;
    if v.status <> 'active' then
        raise exception 'LINK_REVOKED' using errcode = 'P0001';
    end if;

    v_max := coalesce(public.config_int('max_upload_bytes', 10485760), 10485760);

    select count(*) into v_count
    from public.publication_rate_limits r
    where r.publication_link_id = v.link_id
      and r.documented_at > now() - interval '1 hour';

    return jsonb_build_object(
        'subject', jsonb_build_object('id', v.subject_id, 'name', v.subject_name,
                                      'description', v.subject_description),
        'classroom', jsonb_build_object('id', v.classroom_id, 'class_name', v.classroom_name,
                                        'institution_name', v.institution_name,
                                        'field_of_study', v.field_of_study,
                                        'level', v.level, 'study_year', v.study_year,
                                        'academic_year', v.academic_year),
        'teacher_name_hint', v.teacher_name_hint,
        'max_upload_bytes', v_max,
        'allowed_extensions', jsonb_build_array('pdf','doc','docx','ppt','pptx','xls','xlsx','jpg','jpeg','png'),
        'publications_last_hour', v_count
    );
end;
$$;

-- Étape 1 de la publication : réservation du fichier (quota, extension, taille)
create or replace function public.api_begin_upload(
    p_token text,
    p_title text,
    p_description text,
    p_file_name text,
    p_file_size bigint,
    p_teacher_name text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v public.publish_link_by_token%rowtype;
    v_max bigint;
    v_count integer;
    v_limit integer;
    v_ext text;
    v_storage_name text;
    v_doc public.documents;
begin
    select * into v from public.publish_link_by_token(p_token);
    if not found then
        raise exception 'LINK_INVALID' using errcode = 'P0001';
    end if;
    if v.status <> 'active' then
        raise exception 'LINK_REVOKED' using errcode = 'P0001';
    end if;

    if p_title is null or length(btrim(p_title)) < 2 then
        raise exception 'TITLE_REQUIRED' using errcode = 'P0001';
    end if;
    if p_teacher_name is null or length(btrim(p_teacher_name)) < 2 then
        raise exception 'TEACHER_NAME_REQUIRED' using errcode = 'P0001';
    end if;
    if p_file_name is null or length(btrim(p_file_name)) = 0 then
        raise exception 'FILE_REQUIRED' using errcode = 'P0001';
    end if;

    v_ext := lower(split_part(p_file_name, '.', array_length(string_to_array(p_file_name, '.'), 1)));
    if v_ext is null or v_ext not in ('pdf','doc','docx','ppt','pptx','xls','xlsx','jpg','jpeg','png') then
        raise exception 'FILE_TYPE_NOT_ALLOWED' using errcode = 'P0001';
    end if;

    v_max := coalesce(public.config_int('max_upload_bytes', 10485760), 10485760);
    if p_file_size is null or p_file_size <= 0 or p_file_size > v_max then
        raise exception 'FILE_TOO_LARGE' using errcode = 'P0001';
    end if;

    v_limit := coalesce(public.config_int('max_publications_per_link_per_hour', 10), 10)::integer;
    select count(*) into v_count
    from public.publication_rate_limits r
    where r.publication_link_id = v.link_id
      and r.documented_at > now() - interval '1 hour';
    if v_count >= v_limit then
        raise exception 'RATE_LIMITED' using errcode = 'P0001';
    end if;

    v_storage_name := v.subject_id::text || '/' || gen_random_uuid()::text || '.' || v_ext;

    insert into public.documents
        (subject_id, title, description, file_name, file_path, file_type, file_size,
         teacher_display_name, publication_link_id)
    values (v.subject_id, btrim(p_title), nullif(btrim(coalesce(p_description, '')), ''),
            btrim(p_file_name), v_storage_name,
            case v_ext when 'jpeg' then 'jpg' else v_ext end, p_file_size,
            btrim(p_teacher_name), v.link_id)
    returning * into v_doc;

    insert into public.publication_rate_limits (publication_link_id) values (v.link_id);

    return jsonb_build_object(
        'document_id', v_doc.id,
        'file_path', v_doc.file_path,
        'subject_id', v_doc.subject_id,
        'max_upload_bytes', v_max
    );
end;
$$;

-- Étape 2 : confirmation après envoi réel du fichier dans Storage
create or replace function public.api_confirm_upload(p_token text, p_document_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, storage
as $$
declare
    v public.publish_link_by_token%rowtype;
    v_doc public.documents;
    v_obj storage.objects;
begin
    select * into v from public.publish_link_by_token(p_token);
    if not found then
        raise exception 'LINK_INVALID' using errcode = 'P0001';
    end if;

    select * into v_doc from public.documents
    where id = p_document_id and subject_id = v.subject_id;
    if not found then
        raise exception 'DOCUMENT_NOT_FOUND' using errcode = 'P0001';
    end if;

    select * into v_obj from storage.objects
    where bucket_id = 'documents' and name = v_doc.file_path;
    if not found then
        raise exception 'UPLOAD_INCOMPLETE' using errcode = 'P0001';
    end if;

    -- Vérification serveur de la taille réelle
    if (v_obj.metadata ->> 'size')::bigint is distinct from v_doc.file_size then
        delete from storage.objects where id = v_obj.id;
        delete from public.documents where id = v_doc.id;
        raise exception 'FILE_SIZE_MISMATCH' using errcode = 'P0001';
    end if;

    if v.status = 'revoked' then
        delete from storage.objects where id = v_obj.id;
        delete from public.documents where id = v_doc.id;
        raise exception 'LINK_REVOKED' using errcode = 'P0001';
    end if;

    return jsonb_build_object(
        'document', jsonb_build_object(
            'id', v_doc.id, 'title', v_doc.title, 'description', v_doc.description,
            'file_name', v_doc.file_name, 'file_type', v_doc.file_type,
            'file_size', v_doc.file_size, 'teacher_display_name', v_doc.teacher_display_name,
            'created_at', v_doc.created_at, 'subject_id', v_doc.subject_id,
            'subject_name', v.subject_name, 'classroom_name', v.classroom_name
        ),
        'download_url', (select url from storage.create_signed_url('documents', v_doc.file_path, 3600))
    );
end;
$$;

-- ---------------------------------------------------------------------
-- 6. RPC — RESPONSABLE (authentifié)
-- ---------------------------------------------------------------------

create or replace function public.api_my_space(p_classroom_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_c public.classrooms;
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = 'P0001';
    end if;

    select * into v_c from public.classrooms where id = p_classroom_id and owner_id = v_uid;
    if not found then
        raise exception 'CLASSROOM_NOT_FOUND' using errcode = 'P0001';
    end if;

    return jsonb_build_object(
        'classroom', jsonb_build_object(
            'id', v_c.id, 'institution_name', v_c.institution_name,
            'class_name', v_c.class_name, 'field_of_study', v_c.field_of_study,
            'level', v_c.level, 'study_year', v_c.study_year,
            'academic_year', v_c.academic_year, 'join_code', v_c.join_code,
            'is_archived', v_c.is_archived, 'created_at', v_c.created_at
        ),
        'subjects', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', s.id, 'name', s.name, 'description', s.description,
                'position', s.position, 'archived_at', s.archived_at,
                'document_count', (select count(*) from public.documents d where d.subject_id = s.id),
                'link', (
                    select jsonb_build_object(
                        'id', pl.id,
                        'status', pl.status,
                        'token_hint', pl.token_hint,
                        'created_at', pl.created_at
                    )
                    from public.publication_links pl
                    where pl.subject_id = s.id and pl.status = 'active'
                    limit 1
                )
            ) order by s.position, s.name)
            from public.subjects s
            where s.classroom_id = v_c.id
        ), '[]'::jsonb),
        'stats', jsonb_build_object(
            'subjects', (select count(*) from public.subjects where classroom_id = v_c.id and archived_at is null),
            'documents', (select count(*) from public.documents d
                          join public.subjects s on s.id = d.subject_id where s.classroom_id = v_c.id),
            'devices', (select count(*) from public.student_devices where classroom_id = v_c.id)
        )
    );
end;
$$;

create or replace function public.api_create_classroom(
    p_institution_name text,
    p_class_name text,
    p_field_of_study text,
    p_level text,
    p_study_year text,
    p_academic_year text
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_code text;
    v_c public.classrooms;
    v_attempt integer := 0;
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = 'P0001';
    end if;
    if p_academic_year !~ '^[0-9]{4}-[0-9]{4}$' then
        raise exception 'ACADEMIC_YEAR_INVALID' using errcode = 'P0001';
    end if;

    loop
        v_attempt := v_attempt + 1;
        v_code := public.random_join_code(8);
        exit when not exists (select 1 from public.classrooms where join_code = v_code);
        if v_attempt > 12 then
            raise exception 'JOIN_CODE_GENERATION_FAILED' using errcode = 'P0001';
        end if;
    end loop;

    insert into public.classrooms
        (owner_id, institution_name, class_name, field_of_study, level, study_year, academic_year, join_code)
    values (v_uid, btrim(p_institution_name), btrim(p_class_name), btrim(p_field_of_study),
            p_level, btrim(p_study_year), btrim(p_academic_year), v_code)
    returning * into v_c;

    return jsonb_build_object('id', v_c.id, 'join_code', v_c.join_code,
                              'class_name', v_c.class_name, 'created_at', v_c.created_at);
end;
$$;

create or replace function public.api_regenerate_join_code(p_classroom_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_code text;
    v_attempt integer := 0;
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = 'P0001';
    end if;
    if not exists (select 1 from public.classrooms where id = p_classroom_id and owner_id = v_uid) then
        raise exception 'CLASSROOM_NOT_FOUND' using errcode = 'P0001';
    end if;

    loop
        v_attempt := v_attempt + 1;
        v_code := public.random_join_code(8);
        exit when not exists (select 1 from public.classrooms where join_code = v_code);
        if v_attempt > 12 then
            raise exception 'JOIN_CODE_GENERATION_FAILED' using errcode = 'P0001';
        end if;
    end loop;

    update public.classrooms set join_code = v_code where id = p_classroom_id;
    return jsonb_build_object('join_code', v_code);
end;
$$;

-- Création d'une matière + premier lien de publication.
-- Le jeton en clair n'est renvoyé qu'à cet instant (stockage haché).
create or replace function public.api_create_subject(
    p_classroom_id uuid,
    p_name text,
    p_description text default null
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_subject public.subjects;
    v_link public.publication_links;
    v_token text;
    v_position integer;
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = 'P0001';
    end if;
    if not exists (select 1 from public.classrooms where id = p_classroom_id and owner_id = v_uid) then
        raise exception 'CLASSROOM_NOT_FOUND' using errcode = 'P0001';
    end if;

    select coalesce(max(position), 0) + 1 into v_position
    from public.subjects where classroom_id = p_classroom_id;

    insert into public.subjects (classroom_id, name, description, position)
    values (p_classroom_id, btrim(p_name), nullif(btrim(coalesce(p_description, '')), ''), v_position)
    returning * into v_subject;

    v_token := replace(replace(encode(extensions.gen_random_bytes(24), 'base64'), '+', '-'), '/', '_');
    v_token := replace(v_token, '=', '');

    insert into public.publication_links (subject_id, token_hash, token_hint)
    values (v_subject.id, public.sha256_bytes(v_token), right(v_token, 6))
    returning * into v_link;

    return jsonb_build_object(
        'subject', jsonb_build_object(
            'id', v_subject.id, 'name', v_subject.name, 'description', v_subject.description,
            'position', v_subject.position, 'created_at', v_subject.created_at
        ),
        'link', jsonb_build_object('id', v_link.id, 'token', v_token, 'created_at', v_link.created_at)
    );
end;
$$;

-- Rotation du lien : révoque l'ancien et en génère un nouveau
create or replace function public.api_rotate_publication_link(p_subject_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_link public.publication_links;
    v_token text;
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = 'P0001';
    end if;
    if not exists (
        select 1 from public.subjects s
        join public.classrooms c on c.id = s.classroom_id
        where s.id = p_subject_id and c.owner_id = v_uid
    ) then
        raise exception 'SUBJECT_NOT_FOUND' using errcode = 'P0001';
    end if;

    update public.publication_links
    set status = 'revoked', revoked_at = now()
    where subject_id = p_subject_id and status = 'active';

    v_token := replace(replace(encode(extensions.gen_random_bytes(24), 'base64'), '+', '-'), '/', '_');
    v_token := replace(v_token, '=', '');

    insert into public.publication_links (subject_id, token_hash, token_hint)
    values (p_subject_id, public.sha256_bytes(v_token), right(v_token, 6))
    returning * into v_link;

    return jsonb_build_object('link', jsonb_build_object('id', v_link.id, 'token', v_token,
                                                        'created_at', v_link.created_at));
end;
$$;

create or replace function public.api_revoke_publication_link(p_subject_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = 'P0001';
    end if;
    if not exists (
        select 1 from public.subjects s
        join public.classrooms c on c.id = s.classroom_id
        where s.id = p_subject_id and c.owner_id = v_uid
    ) then
        raise exception 'SUBJECT_NOT_FOUND' using errcode = 'P0001';
    end if;

    update public.publication_links
    set status = 'revoked', revoked_at = now()
    where subject_id = p_subject_id and status = 'active';

    return jsonb_build_object('ok', true);
end;
$$;

-- Suppression d'un document : fichier Storage + ligne SQL
create or replace function public.api_delete_document(p_document_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, storage
as $$
declare
    v_uid uuid := auth.uid();
    v_doc public.documents;
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = 'P0001';
    end if;

    select d.* into v_doc
    from public.documents d
    join public.subjects s on s.id = d.subject_id
    join public.classrooms c on c.id = s.classroom_id
    where d.id = p_document_id and c.owner_id = v_uid;

    if not found then
        raise exception 'DOCUMENT_NOT_FOUND' using errcode = 'P0001';
    end if;

    delete from storage.objects where bucket_id = 'documents' and name = v_doc.file_path;
    delete from public.documents where id = v_doc.id;

    return jsonb_build_object('ok', true, 'deleted_id', v_doc.id, 'file_path', v_doc.file_path);
end;
$$;

-- Fermeture d'une classe : les étudiants perdent l'accès, le responsable conserve l'historique
create or replace function public.api_archive_classroom(p_classroom_id uuid, p_archived boolean default true)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = 'P0001';
    end if;
    if not exists (select 1 from public.classrooms where id = p_classroom_id and owner_id = v_uid) then
        raise exception 'CLASSROOM_NOT_FOUND' using errcode = 'P0001';
    end if;

    update public.classrooms set is_archived = coalesce(p_archived, true) where id = p_classroom_id;
    return jsonb_build_object('ok', true, 'is_archived', coalesce(p_archived, true));
end;
$$;

-- Suppression d'une matière (cascade documents + liens)
create or replace function public.api_delete_subject(p_subject_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = public, storage
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED' using errcode = 'P0001';
    end if;
    if not exists (
        select 1 from public.subjects s
        join public.classrooms c on c.id = s.classroom_id
        where s.id = p_subject_id and c.owner_id = v_uid
    ) then
        raise exception 'SUBJECT_NOT_FOUND' using errcode = 'P0001';
    end if;

    delete from storage.objects
    where bucket_id = 'documents'
      and name in (select d.file_path from public.documents d where d.subject_id = p_subject_id);
    delete from public.subjects where id = p_subject_id;

    return jsonb_build_object('ok', true);
end;
$$;

-- ---------------------------------------------------------------------
-- 7. ROW LEVEL SECURITY
-- ---------------------------------------------------------------------

alter table public.profiles enable row level security;
alter table public.classrooms enable row level security;
alter table public.subjects enable row level security;
alter table public.publication_links enable row level security;
alter table public.documents enable row level security;
alter table public.student_sessions enable row level security;
alter table public.student_devices enable row level security;
alter table public.notification_preferences enable row level security;
alter table public.publication_rate_limits enable row level security;
alter table public.access_logs enable row level security;
alter table public.platform_settings enable row level security;

-- Aucun accès direct : les RPC SECURITY DEFINER sont la seule porte d'entrée
revoke all on public.profiles, public.classrooms, public.subjects, public.publication_links,
    public.documents, public.student_sessions, public.student_devices,
    public.notification_preferences, public.publication_rate_limits, public.access_logs,
    public.platform_settings from anon, authenticated;

-- Profils : lecture et mise à jour de son propre profil
grant select, update on public.profiles to authenticated;
create policy profiles_self_select on public.profiles for select to authenticated
    using (id = auth.uid());
create policy profiles_self_update on public.profiles for update to authenticated
    using (id = auth.uid()) with check (id = auth.uid());

-- Classes : lecture et modification par le propriétaire uniquement
grant select, insert, update, delete on public.classrooms to authenticated;
create policy classrooms_owner_select on public.classrooms for select to authenticated
    using (owner_id = auth.uid());
create policy classrooms_owner_insert on public.classrooms for insert to authenticated
    with check (owner_id = auth.uid());
create policy classrooms_owner_update on public.classrooms for update to authenticated
    using (owner_id = auth.uid()) with check (owner_id = auth.uid());
create policy classrooms_owner_delete on public.classrooms for delete to authenticated
    using (owner_id = auth.uid());

-- Matières : via la classe du propriétaire
grant select, insert, update, delete on public.subjects to authenticated;
create policy subjects_owner_select on public.subjects for select to authenticated
    using (exists (select 1 from public.classrooms c where c.id = classroom_id and c.owner_id = auth.uid()));
create policy subjects_owner_insert on public.subjects for insert to authenticated
    with check (exists (select 1 from public.classrooms c where c.id = classroom_id and c.owner_id = auth.uid()));
create policy subjects_owner_update on public.subjects for update to authenticated
    using (exists (select 1 from public.classrooms c where c.id = classroom_id and c.owner_id = auth.uid()))
    with check (exists (select 1 from public.classrooms c where c.id = classroom_id and c.owner_id = auth.uid()));
create policy subjects_owner_delete on public.subjects for delete to authenticated
    using (exists (select 1 from public.classrooms c where c.id = classroom_id and c.owner_id = auth.uid()));

-- Documents : lecture et suppression par le propriétaire de la classe
grant select, delete on public.documents to authenticated;
create policy documents_owner_select on public.documents for select to authenticated
    using (exists (
        select 1 from public.subjects s
        join public.classrooms c on c.id = s.classroom_id
        where s.id = subject_id and c.owner_id = auth.uid()));
create policy documents_owner_delete on public.documents for delete to authenticated
    using (exists (
        select 1 from public.subjects s
        join public.classrooms c on c.id = s.classroom_id
        where s.id = subject_id and c.owner_id = auth.uid()));

-- Aucune policy ouverte sur les jetons, sessions, appareils, quotas, journaux
create policy publication_links_no_access on public.publication_links for select to authenticated using (false);
create policy student_sessions_no_access on public.student_sessions for select to authenticated using (false);
create policy student_devices_no_access on public.student_devices for select to authenticated using (false);
create policy notification_preferences_no_access on public.notification_preferences for select to authenticated using (false);
create policy publication_rate_limits_no_access on public.publication_rate_limits for select to authenticated using (false);
create policy access_logs_no_access on public.access_logs for select to authenticated using (false);
create policy platform_settings_no_access on public.platform_settings for select to authenticated using (false);

-- ---------------------------------------------------------------------
-- 8. PERMISSIONS D'EXÉCUTION DES RPC
-- ---------------------------------------------------------------------

revoke all on function public.api_join_classroom(text, text, text) from public;
revoke all on function public.api_student_classroom(text) from public;
revoke all on function public.api_student_documents(text, uuid, text, integer, integer) from public;
revoke all on function public.api_student_document_link(text, uuid, integer) from public;
revoke all on function public.api_register_device(text, text, text, text, boolean, text, jsonb) from public;
revoke all on function public.api_set_notifications(text, text, boolean, jsonb) from public;
revoke all on function public.api_student_updates(text, timestamptz) from public;
revoke all on function public.api_publish_context(text) from public;
revoke all on function public.api_begin_upload(text, text, text, text, bigint, text) from public;
revoke all on function public.api_confirm_upload(text, uuid) from public;
revoke all on function public.api_my_space(uuid) from public;
revoke all on function public.api_create_classroom(text, text, text, text, text, text) from public;
revoke all on function public.api_regenerate_join_code(uuid) from public;
revoke all on function public.api_create_subject(uuid, text, text) from public;
revoke all on function public.api_rotate_publication_link(uuid) from public;
revoke all on function public.api_revoke_publication_link(uuid) from public;
revoke all on function public.api_delete_document(uuid) from public;
revoke all on function public.api_archive_classroom(uuid, boolean) from public;
revoke all on function public.api_delete_subject(uuid) from public;

grant execute on function public.api_join_classroom(text, text, text) to anon, authenticated;
grant execute on function public.api_student_classroom(text) to anon, authenticated;
grant execute on function public.api_student_documents(text, uuid, text, integer, integer) to anon, authenticated;
grant execute on function public.api_student_document_link(text, uuid, integer) to anon, authenticated;
grant execute on function public.api_register_device(text, text, text, text, boolean, text, jsonb) to anon, authenticated;
grant execute on function public.api_set_notifications(text, text, boolean, jsonb) to anon, authenticated;
grant execute on function public.api_student_updates(text, timestamptz) to anon, authenticated;
grant execute on function public.api_publish_context(text) to anon, authenticated;
grant execute on function public.api_begin_upload(text, text, text, text, bigint, text) to anon, authenticated;
grant execute on function public.api_confirm_upload(text, uuid) to anon, authenticated;
grant execute on function public.api_my_space(uuid) to authenticated;
grant execute on function public.api_create_classroom(text, text, text, text, text, text) to authenticated;
grant execute on function public.api_regenerate_join_code(uuid) to authenticated;
grant execute on function public.api_create_subject(uuid, text, text) to authenticated;
grant execute on function public.api_rotate_publication_link(uuid) to authenticated;
grant execute on function public.api_revoke_publication_link(uuid) to authenticated;
grant execute on function public.api_delete_document(uuid) to authenticated;
grant execute on function public.api_archive_classroom(uuid, boolean) to authenticated;
grant execute on function public.api_delete_subject(uuid) to authenticated;

-- ---------------------------------------------------------------------
-- 9. STOCKAGE PRIVÉ
-- ---------------------------------------------------------------------

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'documents',
    'documents',
    false,
    52428800,
    array[
        'application/pdf',
        'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/vnd.ms-powerpoint',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        'application/vnd.ms-excel',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'image/jpeg',
        'image/png'
    ]
)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- Lecture directe interdite : seules les URLs signées servent les fichiers
drop policy if exists klassio_documents_no_direct_read on storage.objects;
create policy klassio_documents_no_direct_read on storage.objects
    for select to anon, authenticated
    using (bucket_id <> 'documents');

-- Dépôt autorisé uniquement après api_begin_upload() (ligne documents existante)
drop policy if exists klassio_documents_insert_pending on storage.objects;
create policy klassio_documents_insert_pending on storage.objects
    for insert to anon, authenticated
    with check (
        bucket_id = 'documents'
        and exists (
            select 1 from public.documents d
            where d.file_path = storage.objects.name
        )
    );

-- Mise à jour (upsert / expiration) autorisée sur les fichiers réservés
drop policy if exists klassio_documents_update_pending on storage.objects;
create policy klassio_documents_update_pending on storage.objects
    for update to anon, authenticated
    using (
        bucket_id = 'documents'
        and exists (select 1 from public.documents d where d.file_path = storage.objects.name)
    )
    with check (
        bucket_id = 'documents'
        and exists (select 1 from public.documents d where d.file_path = storage.objects.name)
    );

-- ---------------------------------------------------------------------
-- 10. DROITS PAR DÉFAUT (nouvelles tables)
-- ---------------------------------------------------------------------
alter default privileges in schema public revoke all on tables from anon, authenticated;
