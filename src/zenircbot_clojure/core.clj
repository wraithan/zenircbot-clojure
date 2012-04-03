(ns zenircbot-clojure.core
  (:require [clj-redis.client :as redis])
  (:use [irclj.core]
        [cheshire.core]))

(def config (parse-string (slurp "../bot.json") true))
(def server (first (config :servers)))
(def redis-uri
  (str "redis://" ((config :redis) :host)
       ":" ((config :redis) :port)
       "/" ((config :redis) :db)))

;; 2 redis connections are required because once you've used a
;; connection for sub you can no longer do anything else with it.
(def pub (redis/init {:url redis-uri}))
(def sub (redis/init {:url redis-uri}))

(def bot-fnmap {:on-message (fn [{:keys [nick channel message irc]}]
                              (redis/publish pub "in" (generate-string
                                                       {:version 1
                                                        :type "privmsg"
                                                        :data {:message message
                                                               :channel channel
                                                               :sender nick}})))
                :on-part (fn [{:keys [nick reason channel irc]}]
                           (redis/publish pub "in" (generate-string
                                                    {:version 1
                                                     :type "part"
                                                     :data {:channel channel
                                                            :sender nick}})))
                :on-quit (fn [{:keys [nick reason irc]}]
                           (redis/publish pub "in" (generate-string
                                                    {:version 1
                                                     :type "quit"
                                                     :data {:sender nick}})))
                :on-action (fn [{:keys [nick message channel irc]}]
                             (redis/publish pub "in" (generate-string
                                                      {:version 1
                                                       :type "privmsg"
                                                       :data {:nick nick
                                                              :message (str "\u0001ACTION" message "\u0001")
                                                              :channel channel}})))})

(def bot (connect (create-irc {:name (server :nick)
                               :server (server :hostname)
                               :port (server :port)
                               :fnmap bot-fnmap})
                  :channels (server :channels)))

(redis/set pub "zenircbot:nick" (server :nick))

(redis/subscribe sub ["out"]
                 (fn [ch json-msg]
                   (let [message (parse-string json-msg true)]
                     (if (and (= (message :version) 1)
                              (= (message :type) "privmsg"))
                       (send-message bot
                                     ((message :data) :to)
                                     ((message :data) :message))))))

(read-line)
(close bot)
