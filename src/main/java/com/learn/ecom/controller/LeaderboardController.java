package com.learn.ecom.controller;

import com.learn.ecom.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService service;

    @GetMapping
    public List<LeaderboardService.Entry> top(@RequestParam(defaultValue = "10") int n) {
        return service.top(n);
    }

    /** Manual sale recording — for testing without going through OrderService. */
    @PostMapping("/{productId}")
    public String record(@PathVariable String productId,
                         @RequestParam(defaultValue = "1") int units) {
        service.recordSale(productId, units);
        return "ok";
    }
}
