package com.example.telegram_bot.service;

import com.example.telegram_bot.config.BotConfig;
import com.example.telegram_bot.entity.Ads;
import com.example.telegram_bot.entity.Fact;
import com.example.telegram_bot.entity.Joke;
import com.example.telegram_bot.entity.User;
import com.example.telegram_bot.repository.AdsRepository;
import com.example.telegram_bot.repository.FactRepository;
import com.example.telegram_bot.repository.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import com.example.telegram_bot.repository.JokeRepository;
import java.sql.Timestamp;
import java.util.*;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {
    final BotConfig config;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JokeRepository jokeRepository;
    @Autowired
    private AdsRepository adsRepository;
    @Autowired
    private FactRepository factRepository;

    static final String HELP_TEXT = "This bot is created to send a random joke from the database each time you request it.\n\n" +
            "You can execute commands from the main menu on the left or by typing commands manually\n\n" +
            "Type /start to see a welcome message\n\n" +
            "Type /joke to get a random joke\n\n" +
            "Type /fact to get a random cat fact\n\n" +
            "Type /help to see this message again\n";
    static final String YES_BUTTON = "YES";
    static final String NO_BUTTON = "NO";
    static final String ERROR_TEXT = "Error occurred: ";
    static final int MAX_JOKE_ID_MINUS_ONE = 3772;
    static final String NEXT_JOKE = "NEXT_JOKE";
    static final String NEXT_FACT = "NEXT_FACT";


    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "get a welcome message"));
        listOfCommands.add(new BotCommand("/help", "info how to use this bot"));
        listOfCommands.add(new BotCommand("/joke", "get a random joke"));
        listOfCommands.add(new BotCommand("/fact", "get a random fact"));

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            if (messageText.contains("/send") && config.getOwnerId() == chatId) {
                String textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                Iterable<User> users = userRepository.findAll();
                for (User user : users) {
                    prepareAndSendMessage(user.getChatId(), textToSend);
                }
            } else {
                switch (messageText) {
                    case "/start":
                        registerUser(update.getMessage());
                        showStart(chatId, update.getMessage().getChat().getFirstName());
                        try {
                        } catch (Exception e) {
                            log.error(Arrays.toString(e.getStackTrace()));
                        }
                        break;
                    case "/help":
                        prepareAndSendMessage(chatId, HELP_TEXT);
                        break;
                    case "/joke":
                        Optional<Joke> joke = getRandomJoke();
                        joke.ifPresent(randomJoke -> addButtonAndSendMessage(randomJoke.getBody(), chatId, false));
                        break;
                    case "/fact":
                        Optional<Fact> fact = getRandomFact();
                        fact.ifPresent(randomFact -> addButtonAndSendMessage(randomFact.getFact(), chatId, true));
                        break;
                    default:
                        commandNotFound(chatId);
                }
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.equals(NEXT_JOKE)) {
                var joke = getRandomJoke();
                joke.ifPresent(randomJoke -> addButtonAndSendMessage(randomJoke.getBody(), chatId, false));
            }
            else if (callbackData.equals(NEXT_FACT)) {
                var fact = getRandomFact();
                fact.ifPresent(randomFact -> addButtonAndSendMessage(randomFact.getFact(), chatId,true));
            }
            else if (callbackData.equals("YES")) {
                askUser(chatId);

                boolean sendJoke = new Random().nextBoolean();
                if (sendJoke) {
                    Optional<Joke> joke = getRandomJoke();
                    joke.ifPresent(randomJoke -> addButtonAndSendMessage(randomJoke.getBody(), chatId, false));
                } else {
                    Optional<Fact> fact = getRandomFact();
                    fact.ifPresent(randomFact -> addButtonAndSendMessage(randomFact.getFact(), chatId, true));
                }
            } else if (callbackData.equals("NO")) {
                SendMessage message = new SendMessage();
                message.setChatId(String.valueOf(chatId));
                message.setText("Okay, maybe next time! :)");
                send(message);
            }

        }
    }

    private void askUser(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Do you want to hear a joke or a cat fact?");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();

        InlineKeyboardButton yesButton = new InlineKeyboardButton();
        yesButton.setText(YES_BUTTON);
        yesButton.setCallbackData(YES_BUTTON);

        InlineKeyboardButton noButton = new InlineKeyboardButton();
        noButton.setText(NO_BUTTON);
        noButton.setCallbackData(NO_BUTTON);

        rowInline.add(yesButton);
        rowInline.add(noButton);

        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);

        message.setReplyMarkup(markupInline);

        send(message);
    }

    private Optional<Fact> getRandomFact(){
        var restTemplate=new RestTemplate();
    	ResponseEntity<Fact> factEntity = restTemplate.getForEntity("https://catfact.ninja/fact", Fact.class);
        Fact fact = factEntity.getBody();
        factRepository.save(fact);
        return Optional.ofNullable(fact);
   }
    private Optional<Joke> getRandomJoke() {
        var r = new Random();
        var randomId = r.nextInt(MAX_JOKE_ID_MINUS_ONE) + 1;
        return jokeRepository.findById(randomId);
    }

    private void addButtonAndSendMessage(String joke, long chatId, boolean isFact) {
        SendMessage message = new SendMessage();
        message.setText(joke);
        message.setChatId(chatId);

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
        List<InlineKeyboardButton> rowInline = new ArrayList<>();
        var inlinekeyboardButton = new InlineKeyboardButton();
        if (!isFact) {
            inlinekeyboardButton.setCallbackData(NEXT_JOKE);
            inlinekeyboardButton.setText(EmojiParser.parseToUnicode("next joke " + ":rolling_on_the_floor_laughing:"));
        }
        else {
            inlinekeyboardButton.setCallbackData(NEXT_FACT);
            inlinekeyboardButton.setText(EmojiParser.parseToUnicode("next fact " + ":smiley_cat:"));
        }
        rowInline.add(inlinekeyboardButton);
        rowsInline.add(rowInline);
        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);
        send(message);

    }

    private void showStart(long chatId, String name) {
        String answer = EmojiParser.parseToUnicode(
                "Hi, " + name + "! :smile:" + " Nice to meet you!\n");
        sendMessage(answer, chatId);
    }

    private void commandNotFound(long chatId) {
        String answer = EmojiParser.parseToUnicode(
                "Command not recognized, please verify and try again :stuck_out_tongue_winking_eye: ");
        sendMessage(answer, chatId);
    }

    private void executeEditMessageText(String text, Long chatId, long messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setText(text);
        message.setMessageId((int) messageId);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void registerUser(Message msg) {
        if (userRepository.findById(msg.getChatId()).isEmpty()) {
            Long chatId = msg.getChatId();
            Chat chat = msg.getChat();

            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            userRepository.save(user);
            log.info("user saved: " + user);
        }
    }

    private void startCommandReceived(Long chatId, String name) {
        String answer = EmojiParser.parseToUnicode("Hi, " + name + ", nice to meet you!" + ":grin:");
        log.info("Replied to user " + name);
        sendMessage(answer,chatId);
    }

    private void sendMessage(String textToSend, Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        send(message);

    }

    private void send(SendMessage msg) {
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error(Arrays.toString(e.getStackTrace()));
        }
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void prepareAndSendMessage(Long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(textToSend);
        executeMessage(message);
    }

    @Scheduled(cron = "${cron.scheduler}")
    private void sendAds() {
        Iterable<Ads> ads = adsRepository.findAll();
        Iterable<User> users = userRepository.findAll();

        for (Ads ad : ads) {
            for (User user : users) {
                prepareAndSendMessage(user.getChatId(), ad.getAd());
            }
        }
    }

    @Scheduled(cron = "0 * * * * *")
    private void sendAskUser() {
        Iterable<User> users = userRepository.findAll();
        for (User user : users) {
            askUser(user.getChatId());
        }
    }
}
