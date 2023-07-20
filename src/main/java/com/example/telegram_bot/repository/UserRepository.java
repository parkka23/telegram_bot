package com.example.telegram_bot.repository;

import com.example.telegram_bot.entity.User;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {
}
