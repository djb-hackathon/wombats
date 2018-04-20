package com.google.ar.core.examples.java.helloar.price;


import java.math.BigDecimal;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class PropertyPriceLookup {

    /** Generate a random price.
     */
    public BigDecimal getPrice() {
        BigDecimal min = new BigDecimal("10.0");
        BigDecimal max = new BigDecimal("99.99");
        BigDecimal randomBigDecimal = min.add(new BigDecimal(Math.random()).multiply(max.subtract(min)));
        return randomBigDecimal.setScale(2,BigDecimal.ROUND_HALF_UP);
    }
}
