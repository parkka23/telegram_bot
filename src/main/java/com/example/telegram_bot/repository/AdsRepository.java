package com.example.telegram_bot.repository;

import com.example.telegram_bot.entity.Ads;
import com.example.telegram_bot.entity.User;
import org.springframework.data.repository.CrudRepository;

public interface AdsRepository extends CrudRepository<Ads, Long> {
}
