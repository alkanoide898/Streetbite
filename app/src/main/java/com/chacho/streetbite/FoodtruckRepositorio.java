package com.chacho.streetbite;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class FoodtruckRepositorio {

    private static final List<Foodtruck> FOODTRUCKS = Collections.unmodifiableList(
            Arrays.asList(
                    new Foodtruck("Taco Truck", "Tacos y comida mexicana", "tacos",
                            4.8, -36.6068, -72.1032, true, R.drawable.tacos),

                    new Foodtruck("Burger Truck", "Hamburguesas artesanales", "hamburguesas",
                            4.6, -36.6072, -72.1026, true, R.drawable.burguer),

                    new Foodtruck("Pizza Truck", "Pizzas artesanales", "pizza",
                            4.7, -36.6062, -72.1040, true, R.drawable.pizza),

                    new Foodtruck("Completo Express", "Completos y comida rápida", "completos",
                            4.7, -36.6070, -72.1038, true, R.drawable.completos),

                    new Foodtruck("Street Burger", "Hamburguesas artesanales", "hamburguesas",
                            4.9, -36.6064, -72.1024, true, R.drawable.burguer),

                    new Foodtruck("Taco House", "Tacos y comida mexicana", "tacos",
                            4.8, -36.6059, -72.1036, true, R.drawable.tacos)
            )
    );

    private FoodtruckRepositorio() {
    }

    public static List<Foodtruck> obtenerTodos() {
        return FOODTRUCKS;
    }

    public static Foodtruck buscarPorNombre(String nombre) {
        for (Foodtruck f : FOODTRUCKS) {
            if (f.nombre.equals(nombre)) {
                return f;
            }
        }
        return null;
    }
}