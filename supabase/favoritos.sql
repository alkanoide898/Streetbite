-- StreetBite: favoritos personales de cada usuario.
-- Ejecutar completo en Supabase > SQL Editor.
--
-- Cada usuario autenticado puede crear, leer, actualizar y eliminar
-- unicamente sus propios favoritos (auth.uid() = usuario_id).
-- Incluye etiqueta y nota personal privada para ofrecer un CRUD completo.

create table if not exists public.favoritos (
    id uuid primary key default gen_random_uuid(),
    usuario_id uuid not null references auth.users (id) on delete cascade,
    foodtruck_id uuid not null references public.foodtrucks (id) on delete cascade,
    etiqueta text not null default 'Favorito',
    nota text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Un usuario no puede duplicar el mismo foodtruck en sus favoritos.
create unique index if not exists favoritos_usuario_foodtruck_key
    on public.favoritos (usuario_id, foodtruck_id);

create index if not exists favoritos_usuario_id_idx
    on public.favoritos (usuario_id);

-- Seguridad: cada usuario solo puede ver y modificar sus propios favoritos.
alter table public.favoritos enable row level security;

drop policy if exists "favoritos_select_propio" on public.favoritos;
create policy "favoritos_select_propio"
    on public.favoritos
    for select
    to authenticated
    using (auth.uid() = usuario_id);

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
    using (auth.uid() = usuario_id)
    with check (auth.uid() = usuario_id);

drop policy if exists "favoritos_delete_propio" on public.favoritos;
create policy "favoritos_delete_propio"
    on public.favoritos
    for delete
    to authenticated
    using (auth.uid() = usuario_id);

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

-- Verificacion rapida
-- select relrowsecurity from pg_class where oid = 'public.favoritos'::regclass;
