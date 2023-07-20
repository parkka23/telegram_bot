package com.example.telegram_bot.repository;

import com.example.telegram_bot.entity.Joke;
import org.springframework.data.repository.CrudRepository;

public interface JokeRepository extends CrudRepository<Joke, Integer> {

}