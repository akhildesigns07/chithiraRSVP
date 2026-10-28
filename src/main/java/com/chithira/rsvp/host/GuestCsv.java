package com.chithira.rsvp.host;

import com.chithira.rsvp.guest.Guest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Guest list export for hosts. Deliberately omits phone numbers. */
final class GuestCsv {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private GuestCsv() {
    }

    static String write(List<Guest> guests) {
        var sb = new StringBuilder("Name,Sent,Sent at,RSVP,Headcount,Responded at\r\n");
        for (Guest g : guests) {
            sb.append(cell(g.getName())).append(',')
                    .append(g.isSent() ? "Yes" : "No").append(',')
                    .append(time(g.getSentAt())).append(',')
                    .append(Totals.label(g.getRsvpStatus())).append(',')
                    .append(g.getHeadcount() == null ? "" : g.getHeadcount()).append(',')
                    .append(time(g.getRespondedAt())).append("\r\n");
        }
        return sb.toString();
    }

    private static String time(LocalDateTime t) {
        return t == null ? "" : TIME.format(t);
    }

    /** Quotes when needed and neutralises spreadsheet formula prefixes. */
    static String cell(String value) {
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
