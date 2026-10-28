package com.chithira.rsvp.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the shop's details available to every page (homepage, portal footer, invite credits). */
@ControllerAdvice
public class ShopModelAdvice {

    private final AppProperties props;

    public ShopModelAdvice(AppProperties props) {
        this.props = props;
    }

    @ModelAttribute("shop")
    AppProperties.Shop shop() {
        return props.shop();
    }
}
