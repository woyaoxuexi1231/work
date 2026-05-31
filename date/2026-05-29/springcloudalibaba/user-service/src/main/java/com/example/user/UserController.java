package com.example.user;

import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {

    @GetMapping("/user/{id}")
    public String getUser(@PathVariable Long id) {
        return "user:" + id;
    }
}
