package com.teacheragent.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {
    private Long id;
    private String username;
    /** admin / teacher */
    private String role;

    public boolean isAdmin() { return "admin".equalsIgnoreCase(role); }
}
