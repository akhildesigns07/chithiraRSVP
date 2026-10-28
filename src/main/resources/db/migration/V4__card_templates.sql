-- Marigold and Moonlight were replaced by three new designs; move existing events to the closest one.
UPDATE event SET template = 'KERALA_TRADITIONAL' WHERE template = 'MARIGOLD';
UPDATE event SET template = 'CHRISTIAN_ELEGANT' WHERE template = 'MOONLIGHT';
