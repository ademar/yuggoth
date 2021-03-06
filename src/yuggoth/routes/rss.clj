(ns yuggoth.routes.rss
  (:use compojure.core yuggoth.config)  
  (:require [markdown.core :as markdown] 
            [clojure.xml :as xml]
            [clj-rss.core :as rss]
            [noir.response :as resp]
            [yuggoth.models.db :as db]
            [yuggoth.util :as util]))

(def site-url "http://yogthos.net/")

(defn parse-content [content]
  (markdown/md-to-html-string (if (> (count content) 500) (str (.substring content 0 500) " [...]") content)))

(defn posts-to-items [author posts]
  (map
    (fn [{:keys [id title content time]}]
      (let [link (str site-url "blog/" id )]
        {:guid link
         :link link
         :title title
         :dc:creator author
         :description (parse-content content)
         :pubDate time
         :category "clojure"}))
    posts))

(defn make-channel [title author posts]
  (apply 
    (partial rss/channel 
             false
             {:title title 
              :link site-url 
              :description title
              :lastBuildDate (new java.util.Date)
              :dc:creator author
              :sy:updatePeriod "hourly"
              :sy:updateFrequency "1"})
    (posts-to-items author posts)))

(defn feed [admin posts]
  (let [{:keys [handle title]} (db/get-admin)
        posts                  (db/get-posts 10 true)
        feed-title             (or (:title (first posts)) (text :nothing-here))]
    (update-in (make-channel feed-title handle posts)
               [:attrs]
               assoc 
               :xmlns:dc "http://purl.org/dc/elements/1.1/"
               :xmlns:sy "http://purl.org/rss/1.0/modules/syndication/")))

(defroutes rss-routes 
  (GET "/rss" []
       (resp/content-type 
         "application/rss+xml" 
         (new java.io.ByteArrayInputStream 
              (->
                (feed (db/get-admin) (db/get-posts 10 true false))
                xml/emit
                with-out-str
                .getBytes)))))

