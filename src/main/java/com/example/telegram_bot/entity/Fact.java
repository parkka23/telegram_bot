package com.example.telegram_bot.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity(name="factDataTable")
@Data
public class Fact {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String fact;
    private Long length;

}
