package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.domain.MealDish;
import java.text.Normalizer;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class MealPlateEstimate {
    enum Group { VEGETABLE, PROTEIN, STARCH, OTHER, EXCLUDED }

    // Approximate edible volumes. A recorded mL is direct, grams use loose cooked-food densities;
    // discrete items and servings use typical volumes. Opaque combinations retain unknown proportions.
    private enum Food {
        DRINK_OR_CONDIMENT(Group.EXCLUDED, 0, 0, "water|coffee|tea|juice|milk|soda|cola|beers?|wine|broth|dressing|sauce|oil|butter|ketchup|mayonnaise|agua|cafe|zumo|leche|cerveza|vino|caldo|salsa|aceite|mantequilla"),
        LEAFY(Group.VEGETABLE, .30, 240, "lettuce|spinach|kale|arugula|rocket|greens|espinacas?|lechuga|rucula"),
        VEGETABLE(Group.VEGETABLE, .65, 120, "vegetables?|broccoli|cauliflower|carrots?|tomato(es)?|cucumbers?|peppers?|zucchini|courgettes?|asparagus|aubergines?|eggplants?|mushrooms?|cabbage|onions?|verduras?|hortalizas?|brocoli|coliflor|zanahorias?|tomates?|pimientos?|pepinos?|calabacin|berenjenas?|cebollas?|champi[nñ]ones"),
        MEAT(Group.PROTEIN, .95, 105, "beef|pork|chicken|turkey|steaks?|patt(y|ies)|sausages?|frankfurters?|ham|bacon|tofu|tempeh|pollo|pavo|ternera|cerdo|salchichas?|jamon|lomo"),
        FISH(Group.PROTEIN, .95, 100, "fish|salmon|tuna|cod|sea bass|hake|trout|sardines?|squid|prawns?|shrimp|mussels?|cuttlefish|octopus|atun|pescado|merluza|lubina|dorada|trucha|bacalao|sardinas?|calamares?|gambas?|langostinos?|sepia|pulpo|mejillones"),
        EGG(Group.PROTEIN, 1, 50, "eggs?|huevos?"),
        LEGUME(Group.PROTEIN, .75, 120, "chickpeas?|beans?|lentils?|garbanzos?|lentejas?|alubias?"),
        NUT(Group.PROTEIN, .62, 45, "nuts|almonds?|walnuts?|peanuts?|almendras?|nueces|cacahuetes?"),
        POTATO_OR_GRAIN(Group.STARCH, .75, 145, "potato(es)?|fries|rice|pasta|spaghetti|noodles?|quinoa|couscous|oats?|wheat|cereals?|flakes|corn|patatas?|papas|arroz|avena|trigo|espaguetis?|fideos?"),
        BREAD(Group.STARCH, .32, 140, "bread|buns?|rolls?|pita|baguettes?|toast|pan|panecillos?"),
        FRUIT(Group.OTHER, .85, 110, "fruit|bananas?|mandarins?|oranges?|apples?|grapes?|kiwis?|plums?|pears?|peach(es)?|figs?|melon|berries|blueberries|strawberries|manzanas?|platanos?|mandarinas?|naranjas?|uvas?|ciruelas?|peras?|higos?|brevas?|fresas?"),
        OTHER(Group.OTHER, .9, 100, "yog(h)?urt|cheese|chocolate|ice cream|yogur|queso|helado");

        final Group group;
        final double gramsPerMl;
        final double typicalMl;
        final Pattern words;
        Food(Group group, double gramsPerMl, double typicalMl, String words) {
            this.group = group;
            this.gramsPerMl = gramsPerMl;
            this.typicalMl = typicalMl;
            this.words = Pattern.compile("\\b(?:" + words + ")\\b");
        }
    }

    private static final Pattern OPAQUE = Pattern.compile("\\b(pizza|pinsa|sandwich|soup|stew|curry|casserole|lasagna|wrap|sushi|poke|croquettes?|croquetas?|omelettes?|ensaladilla|sopa|guiso|bocadillo)\\b");
    private static final Pattern BUN = Pattern.compile("\\b(hot dog|hamburger|burger) (buns?|rolls?)\\b|\\bpan (de|para) (perritos?|hamburguesas?)\\b");
    private static final Pattern GREEN_BEANS = Pattern.compile("\\b(green beans?|judias? verdes?)\\b");
    private static final Pattern SALAD = Pattern.compile("\\b(salad|ensalada)\\b");
    private static final Pattern DRINK = Pattern.compile("\\b(juice|coffee|tea|water|soda|cola|beer|wine|milk|zumo|cafe|cerveza|vino|leche|agua)\\b");
    private static final Pattern CONDIMENT = Pattern.compile("\\b(dressing|sauce|broth|mayonnaise|oil|butter|ketchup|aceite|mantequilla|salsa|caldo)\\b");
    private static final Pattern MIXING = Pattern.compile("\\b(with|con|in|and|y)\\b");
    private static final Pattern SUPPLEMENT = Pattern.compile("\\b(protein powder|vanilla protein|proteina en polvo)\\b");
    private static final Pattern DESSERT = Pattern.compile("\\b(ice cream|chocolate|cereal bars?|helado)\\b");

    private final EnumMap<Group, Double> volumes = new EnumMap<>(Group.class);
    private boolean unknownIdentity;
    private int identified;
    private final int total;

    MealPlateEstimate(List<MealDish> foods) {
        total = foods.size();
        for (var dish : foods) {
            Food food = classify(dish);
            if (food == null) {
                unknownIdentity = true;
                continue;
            }
            identified++;
            if (food.group == Group.EXCLUDED) continue;
            double quantity = dish.getQuantity().doubleValue();
            double ml = switch (dish.getUnit()) {
                case GRAM -> quantity / food.gramsPerMl;
                case MILLILITRE -> quantity;
                case UNIT, SERVING -> quantity * food.typicalMl;
            };
            volumes.merge(food.group, ml, Double::sum);
        }
    }

    private static Food classify(MealDish dish) {
        String name = Normalizer.normalize(dish.getName().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replace('-', ' ');
        name = name.replace("burger king", "").split("\\b(?:without|sin)\\b", 2)[0];
        if (OPAQUE.matcher(name).find()) return null;
        name = BUN.matcher(name).replaceAll("bun");
        name = GREEN_BEANS.matcher(name).replaceAll("vegetables");
        if (DESSERT.matcher(name).find()) return Food.OTHER;
        if (SUPPLEMENT.matcher(name).find() || DRINK.matcher(name).find()) return Food.DRINK_OR_CONDIMENT;
        var condiment = CONDIMENT.matcher(name);
        if (condiment.find() && !MIXING.matcher(name.substring(0, condiment.start())).find()) {
            return Food.DRINK_OR_CONDIMENT;
        }
        if (dish.isFruit()) return Food.FRUIT;
        Food found = null;
        for (var food : Food.values()) {
            if (food.group == Group.EXCLUDED || !food.words.matcher(name).find()) continue;
            if (found != null && found.group != food.group) return null;
            found = food;
        }
        // Unspecified salads can contain pasta, fruit or meat. Use named ingredients or a saved fruit flag.
        if (SALAD.matcher(name).find() && found != null && found.group != Group.VEGETABLE && found != Food.FRUIT) return null;
        return found;
    }

    Double share(Group group) {
        if (unknownIdentity || volumes.isEmpty()) return null;
        double totalMl = volumes.values().stream().mapToDouble(Double::doubleValue).sum();
        return volumes.getOrDefault(group, 0.0) / totalMl;
    }

    String coverage() {
        return identified + "/" + total + " foods identified; estimated edible volume";
    }
}
