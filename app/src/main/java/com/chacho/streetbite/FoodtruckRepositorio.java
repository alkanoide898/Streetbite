package com.chacho.streetbite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Repositorio de foodtrucks.
 *
 * Mantiene una caché en memoria que se llena desde Supabase
 * (public.foodtrucks). Si la red falla o la tabla todavía no existe,
 * conserva los datos mock para que la app no quede vacía.
 */
public final class FoodtruckRepositorio {

    public interface Callback {
        /**
         * @param foodtrucks    lista disponible (remota o local)
         * @param desdeSupabase true si vino de Supabase
         */
        void onListo(List<Foodtruck> foodtrucks, boolean desdeSupabase);
    }

    private static final List<Foodtruck> MOCK = crearMock();

    private static List<Foodtruck> cache = MOCK;

    private static final List<Callback> pendientes = new ArrayList<>();

    private static boolean cargando = false;

    private FoodtruckRepositorio() {
    }

    /**
     * Carga el catálogo desde Supabase. Si ya hay una carga en curso,
     * solo se registra el callback y se avisa cuando termine.
     */
    public static synchronized void cargar(Callback callback) {

        if (callback != null) {
            pendientes.add(callback);
        }

        if (cargando) {
            return;
        }

        cargando = true;

        SupabaseHelper.listarFoodtrucks(
                new SupabaseHelper.FoodtrucksCallback() {

                    @Override
                    public void onSuccess(List<Foodtruck> foodtrucks) {
                        cache = Collections.unmodifiableList(
                                new ArrayList<>(foodtrucks)
                        );
                        terminar(true);
                    }

                    @Override
                    public void onError(String mensaje) {
                        terminar(false);
                    }

                    @Override
                    public void onSinConexion(String mensaje) {
                        terminar(false);
                    }
                }
        );
    }

    private static void terminar(boolean desdeSupabase) {

        List<Callback> copia;

        synchronized (FoodtruckRepositorio.class) {
            copia = new ArrayList<>(pendientes);
            pendientes.clear();
            cargando = false;
        }

        for (Callback callback : copia) {
            callback.onListo(cache, desdeSupabase);
        }
    }

    public static List<Foodtruck> obtenerTodos() {
        return cache;
    }

    public static Foodtruck buscarPorNombre(String nombre) {
        for (Foodtruck foodtruck : cache) {
            if (foodtruck.nombre.equals(nombre)) {
                return foodtruck;
            }
        }
        return null;
    }

    public static Foodtruck buscarPorId(String id) {
        for (Foodtruck foodtruck : cache) {
            if (foodtruck.id.equals(id)) {
                return foodtruck;
            }
        }
        return null;
    }

    /**
     * Respaldo local con los mismos locales reales de San Carlos, para que
     * la app siga mostrando datos útiles si Supabase no responde.
     * Se mantiene igual que el seed de supabase/foodtrucks.sql.
     */
    private static List<Foodtruck> crearMock() {
        List<Foodtruck> lista = new ArrayList<>();

        lista.add(new Foodtruck(
                "local-kmilo", "K'milo Móvil",
                "Empanadas que se mueven al ritmo de tu antojo.",
                "empanadas, completos, churrascos", 5.0,
                -36.4291462, -71.9517204, true,
                "completos", "Francisco Bilbao 934", "20:00 - 00:00 aprox",
                "+56996873838", ""
        ));

        lista.add(new Foodtruck(
                "local-mechadas", "Las Mechadas de Mechita",
                "Hamburguesas caseras de calidad.",
                "hamburguesas, completos, churrascos", 5.0,
                -36.4318757, -71.9669775, true,
                "burguer", "Sector Alameda (carros)", "12:00 - 00:00",
                "+56962896959", "las_mechadas_de_mechita"
        ));

        lista.add(new Foodtruck(
                "local-bajon", "El Bajón del Tío Pato",
                "¿Tienes hambre? Aquí encuentras lo que necesitas.",
                "papas fritas, completos, sandwichs", 4.3,
                -36.4320716, -71.9665601, true,
                "completos", "Puelma 146", "18:00 - 01:00 aprox",
                "+56992493338", "elbajondeltiopato"
        ));

        lista.add(new Foodtruck(
                "local-playa", "La Playa Food Truck",
                "Sabor de playa, aunque estés lejos del mar.",
                "hamburguesas, empanadas, papas fritas", 4.4,
                -36.4237640, -71.9597775, true,
                "burguer", "Vicuña Mackenna 322", "12:30 - 01:00 aprox",
                "+56949189314", ""
        ));

        lista.add(new Foodtruck(
                "local-west", "West Burguers",
                "Burgers caseras con sabor de barrio.",
                "hamburguesas, papas fritas", 4.0,
                -36.4315510, -71.9675142, true,
                "burguer", "Sector Alameda (carros)", "13:00 - 22:30",
                "+56941612545", "westburguers_sancarlos"
        ));

        lista.add(new Foodtruck(
                "local-rocka", "Rocka Pizza",
                "Pizzas napolitanas cocidas a la piedra, selladas al vacío.",
                "pizzas, sandwichs", 4.6,
                -36.4173749, -71.9545543, true,
                "pizza", "Independencia 1291", "18:00 - 22:30",
                "+56998139170", "Rockapizzasc"
        ));

        lista.add(new Foodtruck(
                "local-party", "PartyBurguer",
                "La fiesta empieza con la primera mordida.",
                "hamburguesas, papas fritas", 4.8,
                -36.4246391, -71.9603147, true,
                "burguer", "Chacabuco 621", "15:00 - 00:00",
                "+56936772284", "partyburguer.cl"
        ));

        lista.add(new Foodtruck(
                "local-supras", "Supras Gourmet",
                "Sushi y hamburguesas con las mejores promociones.",
                "hamburguesas, sushi, completos", 4.9,
                -36.4170421, -71.9547573, true,
                "tacos", "Independencia 1302", "12:00 - 23:00 aprox",
                "+56928160686", "supras_gourmet"
        ));

        lista.add(new Foodtruck(
                "local-agri", "Agri Dulce",
                "Dulce y salado, todo en un mismo lugar.",
                "sushi, pizzas, postres", 4.3,
                -36.4300019, -71.9575272, true,
                "pizza", "Brasil 280", "12:00 - 15:00 / 18:30 - 22:30",
                "+56936434666", "agri.dulce_sancarlos"
        ));

        return Collections.unmodifiableList(lista);
    }
}
