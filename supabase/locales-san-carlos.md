# Locales reales de San Carlos

> **Estado:** los 9 locales entregados ya están cargados en
> `supabase/foodtrucks.sql`. Esta plantilla queda para agregar locales nuevos
> o completar datos que falten (fotos, horarios exactos, etc.).

Plantilla para cargar el catálogo real de StreetBite. Completa una fila por
local. Si un dato no lo tienes, déjalo vacío o escribe `?`.

## Campos

| Campo | Obligatorio | Ejemplo |
|---|---|---|
| `nombre` | Sí | "Donde el Chino" |
| `categoria` | Sí | tacos, hamburguesas, completos, pizza, sushi, empanadas, café... |
| `descripcion` | Sí | "Hamburguesas artesanales y papas rústicas" |
| `direccion` | Sí | "Av. Vicuña Mackenna 123, San Carlos" |
| `horario` | Recomendado | "Lun a sáb 12:00 - 22:00" |
| `abierto` | Sí | `true` / `false` (si está abierto ahora) |
| `rating` | Opcional | 4.5 (de 0 a 5) |
| `lat` / `lon` | Recomendado | -36.4248 / -71.9176 |
| `imagen` | Recomendado | tacos, burguer, pizza, completos |
| `instagram` / `whatsapp` | Opcional | @local / +56912345678 |

### Cómo obtener lat/lon

1. Abre Google Maps y busca el local.
2. Click derecho sobre el punto exacto.
3. Copia las coordenadas (aparecen arriba del menú).
4. Si no puedes, basta con la dirección: la geocodificamos después.

## Tabla para completar

Copia esta tabla, complétala y pásamela en el chat:

| nombre | categoria | descripcion | direccion | horario | abierto | rating | lat | lon | imagen | instagram | whatsapp |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Ejemplo: Donde el Chino | hamburguesas | Hamburguesas artesanales | Av. Vicuña Mackenna 123 | Lun a sáb 12:00 - 22:00 | true | 4.6 | -36.4248 | -71.9176 | burguer | @dondeelchino | +56912345678 |
|  |  |  |  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |  |  |  |
|  |  |  |  |  |  |  |  |  |  |  |  |

## Notas

- Las **categorías ya son dinámicas**: si aparece `sushi`, `empanadas`,
  `café`, etc., la app crea el chip automáticamente. No hay que tocar código.
- Para las **imágenes** por ahora se usan los drawables locales:
  `tacos`, `burguer`, `pizza`, `completos`. Si el local no calza con
  ninguno, se usa `tacos` como respaldo.
- Si tienes fotos reales, más adelante las subimos a Supabase Storage y
  cambiamos `imagen` por una URL.
- El estado `abierto` es manual por ahora. Si quieres, después lo calculamos
  automáticamente a partir del horario.
