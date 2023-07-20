package com.example.telegram_bot.repository;

import com.example.telegram_bot.entity.Fact;
import com.example.telegram_bot.entity.User;
import org.springframework.data.repository.CrudRepository;

public interface FactRepository extends CrudRepository<Fact, Long> {
}
