(ns zenircbot-clojure.core
  (:require [clj-redis.client :as redis])
  (:use [irclj.core]
        [cheshire.core]))

(def db (redis/init))

(def fnmap {:on-message (fn [{:keys [nick channel message irc]}]
                          (redis/publish db "in" (generate-string
                                                  {:version 1
                                                   :type "privmsg"
                                                   :data {:message message
                                                          :channel channel
                                                          :sender nick}})))})

(def bot (connect (create-irc {:name "ZenIRCBot-clojure"
                               :server "irc.freenode.net"
                               :fnmap fnmap})
                  :channels ["#pdxbots"]))

(redis/subscribe db ["out"] (fn [ch msg]
                              (let [message (parse-string msg true)]
                                ((println "say "
                                          ((message :data) :message)
                                          " to "
                                          ((message :data) :to))
                                 (send-message bot
                                               ((message :data) :to)
                                               ((message :data) :message))))))
(read-line)
(close bot)