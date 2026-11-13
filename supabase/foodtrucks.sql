-- StreetBite: catalogo de foodtrucks.
-- Ejecutar completo en Supabase > SQL Editor.
--
-- Tabla pensada para que la app deje de usar datos mock y lea los
-- foodtrucks reales desde Supabase.
--
-- Lectura: publica (anon + authenticated), porque el catalogo se puede
--          mostrar sin haber iniciado sesion.
-- Escritura: solo usuarios autenticados. Cada usuario puede modificar
--            unicamente los foodtrucks que creo (creado_por = auth.uid()).

create table if not exists public.foodtrucks (
    id uuid primary key default gen_random_uuid(),
    nombre text not null,
    descripcion text,
    categoria text not null,
    rating numeric(2, 1) not null default 0
        check (rating >= 0 and rating <= 5),
    lat double precision not null,
    lon double precision not null,
    abierto boolean not null default true,
    imagen text not null default 'tacos',
    direccion text,
    horario text,
    telefono text,
    instagram text,
    creado_por uuid references auth.users (id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Columnas de contacto (para tablas creadas con la version anterior).
alter table public.foodtrucks
    add column if not exists telefono text;
alter table public.foodtrucks
    add column if not exists instagram text;

-- Un foodtruck por nombre (permite el ON CONFLICT del seed).
create unique index if not exists foodtrucks_nombre_key
    on public.foodtrucks (nombre);

create index if not exists foodtrucks_categoria_idx
    on public.foodtrucks (categoria);

-- Seguridad
alter table public.foodtrucks enable row level security;

-- Cualquiera puede ver el catalogo.
drop policy if exists "Foodtrucks visibles para todos" on public.foodtrucks;
create policy "Foodtrucks visibles para todos"
    on public.foodtrucks
    for select
    to anon, authenticated
    using (true);

-- Un usuario autenticado puede agregar foodtrucks.
drop policy if exists "Usuarios pueden crear foodtrucks" on public.foodtrucks;
create policy "Usuarios pueden crear foodtrucks"
    on public.foodtrucks
    for insert
    to authenticated
    with check (auth.uid() = creado_por);

-- Solo el creador puede editar o eliminar su foodtruck.
drop policy if exists "Creadores pueden actualizar foodtrucks" on public.foodtrucks;
create policy "Creadores pueden actualizar foodtrucks"
    on public.foodtrucks
    for update
    to authenticated
    using (auth.uid() = creado_por)
    with check (auth.uid() = creado_por);

drop policy if exists "Creadores pueden eliminar foodtrucks" on public.foodtrucks;
create policy "Creadores pueden eliminar foodtrucks"
    on public.foodtrucks
    for delete
    to authenticated
    using (auth.uid() = creado_por);

-- Permisos de tabla
revoke all on public.foodtrucks from anon;
grant select on public.foodtrucks to anon;
grant select, insert, update, delete on public.foodtrucks to authenticated;

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

drop trigger if exists foodtrucks_updated_at on public.foodtrucks;
create trigger foodtrucks_updated_at
    before update on public.foodtrucks
    for each row
    execute function public.actualizar_updated_at();

-- ============================================================
-- Locales reales de San Carlos (datos entregados por el usuario).
-- La columna "categoria" puede tener varias categorias separadas por
-- coma; la app las separa y genera un chip por cada una.
--
-- Coordenadas validadas con la ficha/geocodificacion de Google Maps
-- (agosto 2026). Las Mechadas de Mechita no tiene ficha propia con
-- direccion exacta: se ubica en el sector Alameda (carros), junto al
-- resto de carros de comida.
-- ============================================================

-- Borra los datos de ejemplo anteriores para no dejar locales falsos.
delete from public.foodtrucks
where nombre in (
    'Taco Truck', 'Burger Truck', 'Pizza Truck',
    'Completo Express', 'Street Burger', 'Taco House',
    'Taco Truck (ejemplo)', 'Burger Truck (ejemplo)', 'Pizza Truck (ejemplo)',
    'Completo Express (ejemplo)', 'Street Burger (ejemplo)', 'Taco House (ejemplo)'
);

insert into public.foodtrucks
    (nombre, descripcion, categoria, rating, lat, lon, abierto, imagen,
     direccion, horario, telefono, instagram)
values
    ('K''milo Móvil',
     'Empanadas que se mueven al ritmo de tu antojo.',
     'empanadas, completos, churrascos', 5.0,
     -36.4291462, -71.9517204, true, 'completos',
     'Francisco Bilbao 934', '20:00 - 00:00 aprox',
     '+56996873838', null),

    ('Las Mechadas de Mechita',
     'Hamburguesas caseras de calidad.',
     'hamburguesas, completos, churrascos', 5.0,
     -36.4318757, -71.9669775, true, 'burguer',
     'Sector Alameda (carros)', '12:00 - 00:00',
     '+56962896959', 'las_mechadas_de_mechita'),

    ('El Bajón del Tío Pato',
     '¿Tienes hambre? Aquí encuentras lo que necesitas.',
     'papas fritas, completos, sandwichs', 4.3,
     -36.4320716, -71.9665601, true, 'completos',
     'Puelma 146', '18:00 - 01:00 aprox',
     '+56992493338', 'elbajondeltiopato'),

    ('La Playa Food Truck',
     'Sabor de playa, aunque estés lejos del mar.',
     'hamburguesas, empanadas, papas fritas', 4.4,
     -36.4237640, -71.9597775, true, 'burguer',
     'Vicuña Mackenna 322', '12:30 - 01:00 aprox',
     '+56949189314', null),

    ('West Burguers',
     'Burgers caseras con sabor de barrio.',
     'hamburguesas, papas fritas', 4.0,
     -36.4315510, -71.9675142, true, 'burguer',
     'Sector Alameda (carros)', '13:00 - 22:30',
     '+56941612545', 'westburguers_sancarlos'),

    ('Rocka Pizza',
     'Pizzas napolitanas cocidas a la piedra, selladas al vacío.',
     'pizzas, sandwichs', 4.6,
     -36.4173749, -71.9545543, true, 'pizza',
     'Independencia 1291', '18:00 - 22:30',
     '+56998139170', 'Rockapizzasc'),

    ('PartyBurguer',
     'La fiesta empieza con la primera mordida.',
     'hamburguesas, papas fritas', 4.8,
     -36.4246391, -71.9603147, true, 'burguer',
     'Chacabuco 621', '15:00 - 00:00',
     '+56936772284', 'partyburguer.cl'),

    ('Supras Gourmet',
     'Sushi y hamburguesas con las mejores promociones.',
     'hamburguesas, sushi, completos', 4.9,
     -36.4170421, -71.9547573, true, 'tacos',
     'Independencia 1302', '12:00 - 23:00 aprox',
     '+56928160686', 'supras_gourmet'),

    ('Agri Dulce',
     'Dulce y salado, todo en un mismo lugar.',
     'sushi, pizzas, postres', 4.3,
     -36.4300019, -71.9575272, true, 'pizza',
     'Brasil 280', '12:00 - 15:00 / 18:30 - 22:30',
     '+56936434666', 'agri.dulce_sancarlos')
on conflict (nombre) do update set
    descripcion = excluded.descripcion,
    categoria = excluded.categoria,
    rating = excluded.rating,
    lat = excluded.lat,
    lon = excluded.lon,
    abierto = excluded.abierto,
    imagen = excluded.imagen,
    direccion = excluded.direccion,
    horario = excluded.horario,
    telefono = excluded.telefono,
    instagram = excluded.instagram,
    updated_at = now();

-- Verificacion rapida
-- select id, nombre, categoria, rating, lat, lon, abierto, imagen,
--        direccion, horario, telefono, instagram
-- from public.foodtrucks order by rating desc;
