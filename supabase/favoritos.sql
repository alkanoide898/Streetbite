-- StreetBite: favoritos personales y compartidos entre usuarios.
-- Ejecutar completo en Supabase > SQL Editor.
--
-- Cada usuario autenticado puede crear, leer, actualizar y eliminar
-- sus propios favoritos, asi como los que hayan sido compartidos con el
-- mediante la funcion RPC compartir_favorito(...).

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

-- Un usuario no puede duplicar el mismo foodtruck como creador.
create unique index if not exists favoritos_usuario_foodtruck_key
    on public.favoritos (usuario_id, foodtruck_id);

create index if not exists favoritos_usuario_id_idx
    on public.favoritos (usuario_id);

create index if not exists favoritos_compartido_con_idx
    on public.favoritos (compartido_con_id);

-- Seguridad: pueden leer, actualizar o quitar el favorito tanto el creador
-- como el usuario registrado con quien se compartio.
alter table public.favoritos enable row level security;

drop policy if exists "favoritos_select_propio" on public.favoritos;
create policy "favoritos_select_propio"
    on public.favoritos
    for select
    to authenticated
    using (auth.uid() = usuario_id or auth.uid() = compartido_con_id);

drop policy if exists "favoritos_insert_propio" on public.favoritos;
create policy "favoritos_insert_propio"
    on public.favoritos
    for insert
    to authenticated
    with check (auth.uid() = usuario_id);

drop policy if exists "favoritos_update_propio" on public.favoritos;
create policy "favoritos_update_propio"
    on public.favoritos
    for update
    to authenticated
    using (auth.uid() = usuario_id or auth.uid() = compartido_con_id)
    with check (auth.uid() = usuario_id or auth.uid() = compartido_con_id);

drop policy if exists "favoritos_delete_propio" on public.favoritos;
create policy "favoritos_delete_propio"
    on public.favoritos
    for delete
    to authenticated
    using (auth.uid() = usuario_id or auth.uid() = compartido_con_id);

-- Permisos de tabla
revoke all on public.favoritos from anon;
grant select, insert, update, delete on public.favoritos to authenticated;

-- Mantener updated_at al dia.
create or replace function public.actualizar_updated_at()
returns trigger
language plpgsql
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

-- Compartir un favorito con otro usuario registrado por su correo electronico.
-- Se ejecuta con SECURITY DEFINER para validar la existencia del destinatario
-- en public.usuarios / auth.users sin exponer la tabla de usuarios al cliente.
create or replace function public.compartir_favorito(
    p_favorito_id uuid,
    p_correo_destino text
)
returns public.favoritos
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    uid uuid := auth.uid();
    correo_limpio text := lower(trim(coalesce(p_correo_destino, '')));
    fav public.favoritos;
    destino_id uuid;
    nombre_destino text;
    nombre_dueno text;
    resultado public.favoritos;
begin
    if uid is null then
        raise exception 'Se requiere una sesion activa.';
    end if;

    if correo_limpio = '' then
        raise exception 'Ingresa el correo del usuario con quien quieres compartir.';
    end if;

    select *
    into fav
    from public.favoritos
    where id = p_favorito_id
      and (usuario_id = uid or compartido_con_id = uid);

    if fav.id is null then
        raise exception 'No se encontro el favorito o no tienes permiso.';
    end if;

    -- Buscar destinatario en public.usuarios y, si falta perfil, en auth.users.
    select usuario_id, coalesce(nullif(trim(nombre), ''), split_part(correo_limpio, '@', 1))
    into destino_id, nombre_destino
    from public.usuarios
    where lower(trim(coalesce(correo, ''))) = correo_limpio
    limit 1;

    if destino_id is null then
        select id,
               coalesce(
                   nullif(trim(coalesce(raw_user_meta_data ->> 'full_name', '')), ''),
                   nullif(trim(coalesce(raw_user_meta_data ->> 'name', '')), ''),
                   split_part(correo_limpio, '@', 1)
               )
        into destino_id, nombre_destino
        from auth.users
        where lower(trim(coalesce(email, ''))) = correo_limpio
        limit 1;
    end if;

    if destino_id is null then
        raise exception 'No existe ningun usuario registrado con ese correo.';
    end if;

    if destino_id = uid or destino_id = fav.usuario_id then
        raise exception 'No puedes compartir un favorito contigo mismo.';
    end if;

    -- Asegurar tambien el nombre visible del creador del favorito.
    select coalesce(nullif(trim(nombre), ''), split_part(coalesce(correo, ''), '@', 1))
    into nombre_dueno
    from public.usuarios
    where usuario_id = fav.usuario_id
    limit 1;

    if coalesce(nombre_dueno, '') = '' then
        select coalesce(
                   nullif(trim(coalesce(raw_user_meta_data ->> 'full_name', '')), ''),
                   nullif(trim(coalesce(raw_user_meta_data ->> 'name', '')), ''),
                   split_part(coalesce(email, ''), '@', 1),
                   'Usuario'
               )
        into nombre_dueno
        from auth.users
        where id = fav.usuario_id
        limit 1;
    end if;

    update public.favoritos
    set dueno_nombre = coalesce(nullif(trim(nombre_dueno), ''), 'Usuario'),
        compartido_con_id = destino_id,
        compartido_con_nombre = coalesce(nullif(trim(nombre_destino), ''), 'Usuario'),
        compartido_con_correo = correo_limpio,
        updated_at = now()
    where id = p_favorito_id
    returning * into resultado;

    return resultado;
end;
$$;

revoke all on function public.compartir_favorito(uuid, text) from public;
grant execute on function public.compartir_favorito(uuid, text) to authenticated;

-- Verificacion rapida
-- select relrowsecurity from pg_class where oid = 'public.favoritos'::regclass;
