package com.chithira.rsvp.config;

import com.chithira.rsvp.event.Event;
import com.chithira.rsvp.guest.Guest;
import org.springframework.stereotype.Component;

/** Absolute links shared outside the app (WhatsApp messages, staff copy/paste). Exposed to templates as {@code @links}. */
@Component("links")
public class Links {

    private final AppProperties props;

    public Links(AppProperties props) {
        this.props = props;
    }

    public String invite(Guest guest) {
        return props.publicBaseUrl() + "/i/" + guest.getToken();
    }

    public String host(Event event) {
        return props.publicBaseUrl() + "/h/" + event.getHostToken();
    }
}
