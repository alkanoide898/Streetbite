-- StreetBite: perfil de usuario vinculado a Supabase Auth.
-- Ejecutar completo en Supabase > SQL Editor.
--
-- Esta version coincide con la tabla real del proyecto:
--   id          uuid PK (se genera solo)
--   usuario_id  uuid -> auth.users(id)
--   nombre      text
--   correo      text
--   created_at  timestamptz
--
-- La tabla usuarios NO guarda contraseñas. Las credenciales viven en
-- auth.users (Supabase Auth) y aqui solo se guarda el perfil publico.

create table if not exists public.usuarios (
    id uuid primary key default gen_random_uuid(),
    usuario_id uuid not null references auth.users (id) on delete cascade,
    nombre text,
    correo text,
    created_at timestamptz not null default now()
);

-- Clave para que funcione ON CONFLICT (usuario_id).
-- En la tabla actual la PK esta en id, por eso hace falta este indice.
create unique index if not exists usuarios_usuario_id_key
    on public.usuarios (usuario_id);

-- Relacion con auth.users si la tabla ya existia sin ella.
do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'public.usuarios'::regclass
          and conname = 'usuarios_usuario_id_fkey'
    ) then
        alter table public.usuarios
            add constraint usuarios_usuario_id_fkey
            foreign key (usuario_id)
            references auth.users (id)
            on delete cascade;
    end if;
end $$;

-- Fecha de creacion automatica.
alter table public.usuarios
    alter column created_at set default now();

update public.usuarios
set created_at = now()
where created_at is null;

-- Seguridad: cada usuario solo puede ver y modificar su propia fila.
alter table public.usuarios enable row level security;

drop policy if exists "usuarios_select_propio" on public.usuarios;
create policy "usuarios_select_propio"
    on public.usuarios for select
    using (auth.uid() = usuario_id);

drop policy if exists "usuarios_insert_propio" on public.usuarios;
create policy "usuarios_insert_propio"
    on public.usuarios for insert
    with check (auth.uid() = usuario_id);

drop policy if exists "usuarios_update_propio" on public.usuarios;
create policy "usuarios_update_propio"
    on public.usuarios for update
    using (auth.uid() = usuario_id)
    with check (auth.uid() = usuario_id);

drop policy if exists "usuarios_delete_propio" on public.usuarios;
create policy "usuarios_delete_propio"
    on public.usuarios for delete
    using (auth.uid() = usuario_id);

revoke all on public.usuarios from anon;
grant select, insert, update, delete on public.usuarios to authenticated;

-- Crea el perfil automaticamente cuando alguien se registra.
-- Asi tambien funciona si el registro exige confirmar el correo.
create or replace function public.crear_perfil_usuario()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.usuarios (usuario_id, nombre, correo)
    values (
        new.id,
        coalesce(
            nullif(new.raw_user_meta_data ->> 'full_name', ''),
            nullif(new.raw_user_meta_data ->> 'name', ''),
            split_part(new.email, '@', 1)
        ),
        new.email
    )
    on conflict (usuario_id) do nothing;

    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;

create trigger on_auth_user_created
    after insert on auth.users
    for each row
    execute function public.crear_perfil_usuario();

-- Eliminacion real de la cuenta.
-- La app no puede borrar filas de auth.users con el token del usuario:
-- esa tabla solo se puede modificar desde el servidor. Esta funcion se
-- ejecuta con los permisos del dueño de la funcion (SECURITY DEFINER) y
-- borra el perfil y la cuenta de Auth de una sola vez.
-- Importante: ejecutar este archivo completo en Supabase > SQL Editor.
create or replace function public.eliminar_mi_cuenta()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    uid uuid := auth.uid();
begin
    if uid is null then
        raise exception 'Se requiere una sesion activa';
    end if;

    delete from public.usuarios where usuario_id = uid;
    delete from auth.users where id = uid;
end;
$$;

revoke all on function public.eliminar_mi_cuenta() from public;
grant execute on function public.eliminar_mi_cuenta() to authenticated;
