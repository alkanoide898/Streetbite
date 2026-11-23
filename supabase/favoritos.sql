-- StreetBite: favoritos personales y compartidos entre usuarios.
-- Ejecutar completo en Supabase > SQL Editor.
--
-- Modelo de seguridad:
--   * Propietario (usuario_id = auth.uid()): puede crear, leer, editar
--     etiqueta/nota, compartir, dejar de compartir y eliminar sus favoritos.
--   * Invitado (compartido_con_id = auth.uid()): puede leer los favoritos
--     compartidos con el y dejar de recibirlos mediante el RPC
--     public.dejar_de_compartir_favorito(...) sin modificar ni borrar el
--     favorito original del propietario.
--   * Las columnas de identidad y comparticion estan protegidas por RLS y un
--     trigger BEFORE UPDATE: solo pueden modificarse a traves de los RPCs
--     SECURITY DEFINER validados.

create table if not exists public.favoritos (
    id uuid primary key default gen_random_uuid(),
    usuario_id uuid not null references auth.users (id) on delete cascade,
    foodtruck_id uuid not null references public.foodtrucks (id) on delete cascade,
    etiqueta text not null default 'Para almorzar',
    nota text not null default '',
    dueno_nombre text not null default '',
    compartido_con_id uuid references auth.users (id) on delete set null,
    compartido_con_nombre text not null default '',
    compartido_con_correo text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Columnas de compartido (para tablas creadas con la version anterior).
alter table public.favoritos
    add column if not exists dueno_nombre text not null default '';
alter table public.favoritos
    add column if not exists compartido_con_id uuid references auth.users (id) on delete set null;
alter table public.favoritos
    add column if not exists compartido_con_nombre text not null default '';
alter table public.favoritos
    add column if not exists compartido_con_correo text not null default '';
alter table public.favoritos
    alter column etiqueta set default 'Para almorzar';

-- Garantizar que los favoritos compartidos existentes tengan un alias visible
-- aunque el cliente ya no seleccione compartido_con_id.
update public.favoritos
set compartido_con_nombre = coalesce(
        nullif(split_part(trim(compartido_con_correo), '@', 1), ''),
        'Usuario'
    )
where compartido_con_id is not null
  and coalesce(trim(compartido_con_nombre), '') = '';

-- Limpiar restos inconsistentes de usuarios eliminados previamente.
update public.favoritos
set compartido_con_nombre = '',
    compartido_con_correo = ''
where compartido_con_id is null
  and (
      coalesce(trim(compartido_con_nombre), '') <> ''
      or coalesce(trim(compartido_con_correo), '') <> ''
  );

-- Un usuario no puede duplicar el mismo foodtruck como creador.
create unique index if not exists favoritos_usuario_foodtruck_key
    on public.favoritos (usuario_id, foodtruck_id);

create index if not exists favoritos_usuario_id_idx
    on public.favoritos (usuario_id);

create index if not exists favoritos_compartido_con_idx
    on public.favoritos (compartido_con_id);

-- Tabla privada para limitar intentos de public.compartir_favorito y mitigar
-- la enumeracion masiva de correos registrados. Sin politicas RLS para roles
-- de cliente: solo los RPCs SECURITY DEFINER acceden a ella.
create table if not exists public.favoritos_compartir_intentos (
    id bigint generated always as identity primary key,
    usuario_id uuid not null references auth.users (id) on delete cascade,
    creado_en timestamptz not null default now()
);

create index if not exists favoritos_compartir_intentos_usuario_fecha_idx
    on public.favoritos_compartir_intentos (usuario_id, creado_en desc);

alter table public.favoritos_compartir_intentos enable row level security;
revoke all on public.favoritos_compartir_intentos from public, anon, authenticated;

-- ============================================================================
-- POLITICAS RLS DE public.favoritos
-- ============================================================================
alter table public.favoritos enable row level security;

-- Lectura: propietario e invitado pueden visualizar el favorito.
drop policy if exists "favoritos_select_propio" on public.favoritos;
create policy "favoritos_select_propio"
    on public.favoritos
    for select
    to authenticated
    using (auth.uid() = usuario_id or auth.uid() = compartido_con_id);

-- Creacion: solo el propietario y sin inyectar campos de comparticion directos.
drop policy if exists "favoritos_insert_propio" on public.favoritos;
create policy "favoritos_insert_propio"
    on public.favoritos
    for insert
    to authenticated
    with check (
        auth.uid() = usuario_id
        and compartido_con_id is null
        and coalesce(trim(compartido_con_nombre), '') = ''
        and coalesce(trim(compartido_con_correo), '') = ''
    );

-- Actualizacion directa: exclusivamente el propietario (etiqueta y nota).
drop policy if exists "favoritos_update_propio" on public.favoritos;
create policy "favoritos_update_propio"
    on public.favoritos
    for update
    to authenticated
    using (auth.uid() = usuario_id)
    with check (auth.uid() = usuario_id);

-- Eliminacion: exclusivamente el propietario. El invitado usa el RPC
-- dejar_de_compartir_favorito(...) para desvincularse sin borrar el original.
drop policy if exists "favoritos_delete_propio" on public.favoritos;
create policy "favoritos_delete_propio"
    on public.favoritos
    for delete
    to authenticated
    using (auth.uid() = usuario_id);

-- Permisos de tabla
revoke all on public.favoritos from public, anon;
grant select, insert, update, delete on public.favoritos to authenticated;

-- ============================================================================
-- TRIGGERS DE INTEGRIDAD Y UPDATED_AT
-- ============================================================================
create or replace function public.actualizar_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists favoritos_updated_at on public.favoritos;
create trigger favoritos_updated_at
    before update on public.favoritos
    for each row
    execute function public.actualizar_updated_at();

-- Impide que un PATCH directo desde PostgREST (rol anon/authenticated) altere
-- columnas sensibles de propiedad o comparticion. Los RPCs SECURITY DEFINER se
-- ejecutan como el dueno de la funcion (no como authenticated) y si pueden
-- actualizar los campos de comparticion tras validar permisos.
create or replace function public.proteger_columnas_favoritos()
returns trigger
language plpgsql
set search_path = public
as $$
begin
    if current_user in ('anon', 'authenticated') then
        if new.id is distinct from old.id
            or new.usuario_id is distinct from old.usuario_id
            or new.foodtruck_id is distinct from old.foodtruck_id
            or new.created_at is distinct from old.created_at
            or new.dueno_nombre is distinct from old.dueno_nombre
            or new.compartido_con_id is distinct from old.compartido_con_id
            or new.compartido_con_nombre is distinct from old.compartido_con_nombre
            or new.compartido_con_correo is distinct from old.compartido_con_correo
        then
            raise exception 'No tienes permiso para modificar estos campos del favorito.'
                using errcode = '42501';
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists favoritos_proteger_columnas on public.favoritos;
create trigger favoritos_proteger_columnas
    before update on public.favoritos
    for each row
    execute function public.proteger_columnas_favoritos();

-- ============================================================================
-- RPC: COMPARTIR FAVORITO (SOLO PROPIETARIO, CON RATE LIMIT Y MINIMIZACION PII)
-- ============================================================================
-- Nota: cambiamos el tipo de retorno a jsonb para no exponer compartido_con_id
-- (UUID interno del destinatario) al propietario en la respuesta del RPC.
drop function if exists public.compartir_favorito(uuid, text);

create or replace function public.compartir_favorito(
    p_favorito_id uuid,
    p_correo_destino text
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    uid uuid := auth.uid();
    correo_limpio text := lower(trim(coalesce(p_correo_destino, '')));
    correo_propio text := lower(trim(coalesce(auth.jwt() ->> 'email', '')));
    intentos_recientes integer := 0;
    fav public.favoritos;
    destino_id uuid;
    alias_destino text;
    nombre_dueno text;
    resultado public.favoritos;
begin
    if uid is null then
        raise exception 'Se requiere una sesion activa.';
    end if;

    if correo_limpio = ''
        or correo_limpio !~* '^[^@\s]+@[^@\s]+\.[^@\s]+$'
    then
        raise exception 'Ingresa un correo electronico valido.';
    end if;

    if correo_propio <> '' and correo_limpio = correo_propio then
        raise exception 'No puedes compartir un favorito contigo mismo.';
    end if;

    -- Solo el propietario del favorito puede compartirlo o cambiar el destino.
    select *
    into fav
    from public.favoritos
    where id = p_favorito_id
      and usuario_id = uid;

    if fav.id is null then
        raise exception 'No se encontro el favorito o solo el propietario puede compartirlo.';
    end if;

    -- Rate limit: maximo 10 intentos por usuario en una ventana de 15 minutos.
    delete from public.favoritos_compartir_intentos
    where usuario_id = uid
      and creado_en < now() - interval '1 hour';

    select count(*)
    into intentos_recientes
    from public.favoritos_compartir_intentos
    where usuario_id = uid
      and creado_en >= now() - interval '15 minutes';

    if intentos_recientes >= 10 then
        raise exception 'Demasiados intentos al compartir. Espera unos minutos e intenta de nuevo.';
    end if;

    insert into public.favoritos_compartir_intentos (usuario_id)
    values (uid);

    -- Buscar destinatario sin extraer metadatos personales completos de auth.users.
    -- Solo se toma el primer nombre desde public.usuarios o el alias del correo.
    select usuario_id,
           coalesce(
               nullif(left(split_part(trim(coalesce(nombre, '')), ' ', 1), 24), ''),
               left(split_part(correo_limpio, '@', 1), 24)
           )
    into destino_id, alias_destino
    from public.usuarios
    where lower(trim(coalesce(correo, ''))) = correo_limpio
    limit 1;

    if destino_id is null then
        select id, left(split_part(correo_limpio, '@', 1), 24)
        into destino_id, alias_destino
        from auth.users
        where lower(trim(coalesce(email, ''))) = correo_limpio
        limit 1;
    end if;

    -- Mensaje generico: evita confirmar si un correo esta o no registrado.
    if destino_id is null or destino_id = uid then
        raise exception 'No se pudo compartir con ese correo. Verifica que este escrito correctamente y corresponda a otra cuenta activa.';
    end if;

    -- Alias visible del propietario para que el invitado sepa quien compartio.
    select coalesce(
               nullif(left(split_part(trim(coalesce(nombre, '')), ' ', 1), 24), ''),
               left(split_part(coalesce(correo, ''), '@', 1), 24),
               'Usuario'
           )
    into nombre_dueno
    from public.usuarios
    where usuario_id = fav.usuario_id
    limit 1;

    if coalesce(trim(nombre_dueno), '') = '' then
        nombre_dueno := coalesce(
            nullif(left(split_part(correo_propio, '@', 1), 24), ''),
            'Usuario'
        );
    end if;

    update public.favoritos
    set dueno_nombre = coalesce(nullif(trim(nombre_dueno), ''), 'Usuario'),
        compartido_con_id = destino_id,
        compartido_con_nombre = coalesce(nullif(trim(alias_destino), ''), 'Usuario'),
        compartido_con_correo = correo_limpio,
        updated_at = now()
    where id = p_favorito_id
      and usuario_id = uid
    returning * into resultado;

    -- Se omite compartido_con_id para no revelar el UUID interno de otro usuario.
    return jsonb_build_object(
        'id', resultado.id,
        'usuario_id', resultado.usuario_id,
        'foodtruck_id', resultado.foodtruck_id,
        'etiqueta', resultado.etiqueta,
        'nota', resultado.nota,
        'dueno_nombre', resultado.dueno_nombre,
        'esta_compartido', true,
        'compartido_con_nombre', resultado.compartido_con_nombre,
        'compartido_con_correo', resultado.compartido_con_correo,
        'created_at', resultado.created_at,
        'updated_at', resultado.updated_at
    );
end;
$$;

revoke all on function public.compartir_favorito(uuid, text) from public, anon;
grant execute on function public.compartir_favorito(uuid, text) to authenticated;

-- ============================================================================
-- RPC: DEJAR DE COMPARTIR / DEJAR DE RECIBIR FAVORITO (PROPIETARIO O INVITADO)
-- ============================================================================
-- Evita el fallo de WITH CHECK en RLS cuando el invitado se desvincula:
--   * Si llama el propietario: quita la comparticion y devuelve su favorito.
--   * Si llama el invitado: lo desvincula sin eliminar el favorito original del
--     propietario y devuelve solo {id, retirado: true}.
drop function if exists public.dejar_de_compartir_favorito(uuid);

create or replace function public.dejar_de_compartir_favorito(
    p_favorito_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    uid uuid := auth.uid();
    fav public.favoritos;
    resultado public.favoritos;
    es_dueno boolean;
begin
    if uid is null then
        raise exception 'Se requiere una sesion activa.';
    end if;

    select *
    into fav
    from public.favoritos
    where id = p_favorito_id
      and (usuario_id = uid or compartido_con_id = uid);

    if fav.id is null then
        raise exception 'No se encontro el favorito o no tienes permiso.';
    end if;

    es_dueno := (fav.usuario_id = uid);

    update public.favoritos
    set compartido_con_id = null,
        compartido_con_nombre = '',
        compartido_con_correo = '',
        updated_at = now()
    where id = p_favorito_id
    returning * into resultado;

    if not es_dueno then
        return jsonb_build_object(
            'id', resultado.id,
            'retirado', true
        );
    end if;

    return jsonb_build_object(
        'id', resultado.id,
        'usuario_id', resultado.usuario_id,
        'foodtruck_id', resultado.foodtruck_id,
        'etiqueta', resultado.etiqueta,
        'nota', resultado.nota,
        'dueno_nombre', resultado.dueno_nombre,
        'esta_compartido', false,
        'compartido_con_nombre', '',
        'compartido_con_correo', '',
        'created_at', resultado.created_at,
        'updated_at', resultado.updated_at
    );
end;
$$;

revoke all on function public.dejar_de_compartir_favorito(uuid) from public, anon;
grant execute on function public.dejar_de_compartir_favorito(uuid) to authenticated;

-- Verificacion rapida:
-- select relrowsecurity from pg_class where oid = 'public.favoritos'::regclass;
-- select policyname, cmd, qual, with_check from pg_policies where tablename = 'favoritos';
