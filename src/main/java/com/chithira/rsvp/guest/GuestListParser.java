package com.chithira.rsvp.guest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parses pasted "Name, phone" lines. Blank lines are ignored; every other line must be valid. */
public final class GuestListParser {

    public record ParsedGuest(int lineNumber, String name, String phone) {
    }

    public record LineError(int lineNumber, String line, String message) {
    }

    public record Result(List<ParsedGuest> guests, List<LineError> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    static final int MAX_NAME_LENGTH = 200;

    private GuestListParser() {
    }

    public static Result parse(String text) {
        List<ParsedGuest> guests = new ArrayList<>();
        List<LineError> errors = new ArrayList<>();
        Set<String> seenPhones = new HashSet<>();
        if (text == null) {
            return new Result(guests, errors);
        }

        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            int lineNumber = i + 1;
            if (line.isEmpty()) {
                continue;
            }
            // Split on the last comma so the phone is always the final field.
            int comma = line.lastIndexOf(',');
            if (comma < 0) {
                errors.add(new LineError(lineNumber, line, "Expected \"Name, phone\""));
                continue;
            }
            String name = line.substring(0, comma).strip();
            String rawPhone = line.substring(comma + 1).strip();
            if (name.isEmpty()) {
                errors.add(new LineError(lineNumber, line, "Name is missing"));
                continue;
            }
            if (name.length() > MAX_NAME_LENGTH) {
                errors.add(new LineError(lineNumber, line, "Name is too long"));
                continue;
            }
            var phone = PhoneNormalizer.normalize(rawPhone);
            if (phone.isEmpty()) {
                errors.add(new LineError(lineNumber, line, "Not a valid Indian or UAE mobile number"));
                continue;
            }
            if (!seenPhones.add(phone.get())) {
                errors.add(new LineError(lineNumber, line, "Phone number appears more than once"));
                continue;
            }
            guests.add(new ParsedGuest(lineNumber, name, phone.get()));
        }
        return new Result(guests, errors);
    }
}
