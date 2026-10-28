package com.chithira.rsvp.host;

import com.chithira.rsvp.guest.Guest;
import com.chithira.rsvp.guest.RsvpStatus;
import java.util.List;

public record Totals(int invited, int sent, int attending, int headcount, int notAttending, int pending) {

    public static Totals of(List<Guest> guests) {
        int sent = 0, attending = 0, headcount = 0, notAttending = 0, pending = 0;
        for (Guest g : guests) {
            if (g.isSent()) {
                sent++;
            }
            switch (g.getRsvpStatus()) {
                case ATTENDING -> {
                    attending++;
                    headcount += g.getHeadcount() == null ? 0 : g.getHeadcount();
                }
                case NOT_ATTENDING -> notAttending++;
                case PENDING -> pending++;
            }
        }
        return new Totals(guests.size(), sent, attending, headcount, notAttending, pending);
    }

    static String label(RsvpStatus status) {
        return switch (status) {
            case ATTENDING -> "Attending";
            case NOT_ATTENDING -> "Not attending";
            case PENDING -> "Pending";
        };
    }
}
