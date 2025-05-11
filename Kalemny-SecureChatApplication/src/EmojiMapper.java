/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author seifa
 */


import java.util.HashMap;
import java.util.Map;

public class EmojiMapper {
    public static Map<String, String> emojiShortcuts = new HashMap<>();
    
    static {
        // Define some emoji shortcuts
        emojiShortcuts.put(":smile:", "😊");
        emojiShortcuts.put(":laugh:", "😂");
        emojiShortcuts.put(":wink:", "😉");
        emojiShortcuts.put(":heart:", "❤️");
        emojiShortcuts.put(":sad:", "😞");
        emojiShortcuts.put(":thumbsup:", "👍");
        emojiShortcuts.put(":cry:", "😭");
        emojiShortcuts.put(":fire:", "🔥");
        emojiShortcuts.put(":sunglasses:", "😎");
        emojiShortcuts.put(":star:", "⭐");
    }

    // Convert emoji codes to actual emojis
    public static String convertEmojis(String message) {
        for (Map.Entry<String, String> entry : emojiShortcuts.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        return message;
    }
}

