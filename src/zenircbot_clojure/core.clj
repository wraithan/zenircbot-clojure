(ns zenircbot-clojure.core
  (:require [clj-redis.client :as redis])
  (:use [irclj.core]
        [cheshire.core]))

(def pub (redis/init))
(def sub (redis/init))

(def bot-fnmap {:on-message (fn [{:keys [nick channel message irc]}]
                              (redis/publish pub "in" (generate-string
                                                       {:version 1
                                                        :type "privmsg"
                                                        :data {:message message
                                                               :channel channel
                                                               :sender nick}})))})

(def bot (connect (create-irc {:name "ZenIRCBot-clojure"
                               :server "irc.freenode.net"
                               :fnmap bot-fnmap})
                  :channels ["#pdxbots"]))

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
