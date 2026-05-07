package com.teacheragent.controller;

import com.teacheragent.common.R;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @GetMapping("/encoding")
    public R<Map<String, Object>> encoding() {
        Map<String, Object> m = new HashMap<>();
        m.put("file.encoding", System.getProperty("file.encoding"));
        m.put("native.encoding", System.getProperty("native.encoding"));
        m.put("sun.jnu.encoding", System.getProperty("sun.jnu.encoding"));
        m.put("default-charset", Charset.defaultCharset().name());
        return R.ok(m);
    }

    @PostMapping("/multipart-echo")
    public R<Map<String, Object>> multipartEcho(@RequestParam(required = false) String text,
                                                @RequestPart(required = false) MultipartFile file) {
        Map<String, Object> m = new HashMap<>();
        m.put("text", text);
        m.put("text-bytes-iso", text == null ? null : bytesHex(text.getBytes(StandardCharsets.ISO_8859_1)));
        m.put("text-bytes-utf8", text == null ? null : bytesHex(text.getBytes(StandardCharsets.UTF_8)));
        m.put("text-as-utf8-from-iso", text == null ? null : new String(text.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
        m.put("file", file == null ? null : file.getOriginalFilename());
        return R.ok(m);
    }

    @PostMapping("/multipart-part-echo")
    public R<Map<String, Object>> multipartPartEcho(@RequestPart(value = "text", required = false) String text) {
        Map<String, Object> m = new HashMap<>();
        m.put("text", text);
        m.put("text-bytes-utf8", text == null ? null : bytesHex(text.getBytes(StandardCharsets.UTF_8)));
        return R.ok(m);
    }

    private String bytesHex(byte[] bs) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bs) sb.append(String.format("%02x ", b));
        return sb.toString();
    }
}
