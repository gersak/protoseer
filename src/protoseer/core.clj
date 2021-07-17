(ns protoseer.core
  (:require
    [clojure.string :as str])
  (:import
    [com.google.protobuf 
     DynamicMessage Value NullValue
     Timestamp Struct ListValue
     Empty Descriptors$FieldDescriptor$JavaType]))


(defn ->kebab-case [x]
  (keyword 
    (str/replace
      (str/lower-case x)
      #"[-_\s]+" "-")))


(defn ->snake-case [x]
  (str/replace
    (str/lower-case (name x))
    #"[-_\s]+" "_"))

(comment
  (->snake-case (->kebab-case "vjaio Cjiqo *21")))

(def ^:dynamic *key-transform*
  {:in ->kebab-case
   :out ->snake-case})

(def empty-proto
  (.build (Empty/newBuilder)))

(defprotocol ProtocolBufferSerdes 
  (->struct [this] "Serializes current object")
  (<-pb [this] "Deserializes current object"))


(def descriptor->type
  {Descriptors$FieldDescriptor$JavaType/INT :int
   Descriptors$FieldDescriptor$JavaType/ENUM :enum
   Descriptors$FieldDescriptor$JavaType/LONG :long
   Descriptors$FieldDescriptor$JavaType/FLOAT :float
   Descriptors$FieldDescriptor$JavaType/DOUBLE :double
   Descriptors$FieldDescriptor$JavaType/STRING :string
   Descriptors$FieldDescriptor$JavaType/BOOLEAN :boolean
   Descriptors$FieldDescriptor$JavaType/MESSAGE :message
   Descriptors$FieldDescriptor$JavaType/BYTE_STRING :bytes})


(declare <-message-descriptor)


(defn <-field-descriptor 
  ([fd]
   (let [{:keys [type] :as data}
         (zipmap
           [:name :type :number :required? :repeated?]
           ((juxt 
              #(.getName %)
              #(get descriptor->type (.getJavaType %))
              #(.getNumber %)
              #(.isRequired %)
              #(.isRepeated %))
            fd))]
     (cond-> data
       (= type :enum)
       (assoc 
         :enum/options 
         (mapv 
           #(keyword (.getName %)) 
           (.getValues (.getEnumType fd)))
         :enum/full-name (.getFullName (.getEnumType fd)))
       ;;
       (= type :message)
       (assoc :message/type (.getFullName (.getMessageType fd)))
       ;;
       (.hasDefaultValue fd)
       (assoc :default (.getDefaultValue fd))))))


(defn <-message-descriptor
  ([md]
   {:name (.getName md)
    :full-name (.getFullName md)
    :fields (mapv <-field-descriptor (.getFields md))}))

(def ^:dynamic *struct-root* true)

(extend-protocol ProtocolBufferSerdes
  java.lang.Boolean
  (->struct [this] (.build (doto (Value/newBuilder) (.setBoolValue this))))
  (<-pb [this] this)
  java.lang.String
  (->struct [this] (.build (doto (Value/newBuilder) (.setStringValue this))))
  (<-pb [this] this)
  java.lang.Float
  (->struct [this] (.build (doto (Value/newBuilder) (.setNumberValue this))))
  (<-pb [this] this)
  java.lang.Double
  (->struct [this] (.build (doto (Value/newBuilder) (.setNumberValue this))))
  (<-pb [this] this)
  java.lang.Integer
  (->struct [this] (.build (doto (Value/newBuilder) (.setNumberValue this))))
  (<-pb [this] this)
  java.lang.Long
  (->struct [this] (.build (doto (Value/newBuilder) (.setNumberValue this))))
  (<-pb [this] this)
  ;; Clojure
  clojure.lang.APersistentSet
  (->struct [this] 
    (.build 
      (doto 
        (Value/newBuilder) 
        (.setListValue 
          (doto
            (ListValue/newBuilder)
            (.addAllValues (map ->struct this)))))))
  clojure.lang.APersistentVector
  (->struct [this] 
    (.build 
      (doto 
        (Value/newBuilder) 
        (.setListValue 
          (doto
            (ListValue/newBuilder)
            (.addAllValues (map ->struct this)))))))
  clojure.lang.PersistentList
  (->struct [this] 
    (.build 
      (doto 
        (Value/newBuilder) 
        (.setListValue 
          (doto
            (ListValue/newBuilder)
            (.addAllValues (map ->struct this)))))))
  (<-pb [this] (map <-pb (.getValuesList this)))
  clojure.lang.LazySeq
  (->struct [this] 
    (.build 
      (doto 
        (Value/newBuilder) 
        (.setListValue 
          (doto
            (ListValue/newBuilder)
            (.addAllValues (map ->struct this)))))))
  clojure.lang.APersistentMap
  (->struct 
    ([this] 
     (let [b (Struct/newBuilder)]
       (.putAllFields 
         b (binding [*struct-root* false]
             (reduce-kv
               (fn [r k v]
                 (assoc r ((:out *key-transform*) (name k))
                   (->struct v)))
               {}
               this)))
       (if *struct-root* 
         (.build b)
         (.build (doto (Value/newBuilder) (.setStructValue b)))))))
  ;;

  (<-pb [this]
    (let [fields (.getAllFields this)]
      (reduce
        (fn [r [k v]]
          (assoc r ((:in *key-transform*) (.getName k)) (<-pb v)))
        {}
        fields)))
  com.google.protobuf.Empty
  (<-pb [this] nil)
  ;;
  nil
  (->struct [this] 
    (.build
      (doto (Value/newBuilder)
        (.setNullValue (NullValue/forNumber 0)))))
  ;;
  com.google.protobuf.Int64Value
  (<-pb [this] (.getValue this))
  ;;
  com.google.protobuf.Int32Value
  (<-pb [this] (.getValue this))
  ;;
  com.google.protobuf.FloatValue
  (<-pb [this] (.getValue this))
  ;;
  com.google.protobuf.DoubleValue
  (<-pb [this] (.getValue this))
  ;;
  com.google.protobuf.StringValue
  (<-pb [this] (.getValue this))
  ;;
  com.google.protobuf.Timestamp
  (<-pb [this]
    (let [i (java.time.Instant/ofEpochSecond (.getSeconds this))]
      (java.util.Date/from (.plusNanos i (.getNanos this)))))
  ;;
  com.google.protobuf.MapEntry
  (<-pb [this]
    (let [k ((:in *key-transform*) (.getKey this))
          v (<-pb (.getValue this))]
      (clojure.lang.MapEntry/create k v)))
  com.google.protobuf.Struct
  (<-pb [this]
    (let [fsmp (.getFieldsMap this)]
      (reduce
        (fn [r [k v]]
          (assoc r ((:in *key-transform*) k) (<-pb v)))
        {}
        fsmp)))
  com.google.protobuf.ListValue
  (<-pb [this]
    (map <-pb (.getValuesList this)))
  ;;
  com.google.protobuf.Descriptors$EnumValueDescriptor
  (<-pb [this] (keyword (.getName this)))
  ;;
  com.google.protobuf.AbstractMessage
  (<-pb [this]
    (let [fields (.getAllFields this)]
      (reduce
        (fn [r [desc v]]
          (assoc r ((:in *key-transform*) (.getName desc)) 
            (cond
              (.isMapField desc)
              (reduce conj {} (map <-pb v))
              ;;
              (.isRepeated desc)
              (mapv <-pb v)
              ;;
              :else (<-pb v))))
        {}
        fields))))


(defn ->pb
  ([data message-descriptor]
   (let [b (.toBuilder (DynamicMessage/getDefaultInstance message-descriptor))]
     (.build
       (reduce-kv
         (fn [data' k v]
           (let [field-name ((:out *key-transform*) k)
                 field-descriptor (.findFieldByName message-descriptor field-name)
                 descriptor-type (descriptor->type (.getJavaType field-descriptor))]
             (case descriptor-type
               ;;
               :message (let [field-message-descriptor (.getMessageType field-descriptor)]
                          (case (.getFullName field-message-descriptor)
                            ;;
                            ("google.protobuf.Struct") (.setField data' field-descriptor (->struct v))
                            ;;
                            "google.protobuf.Timestamp"
                            (let [i (.toInstant v)]
                              (.setField
                                data'
                                field-descriptor
                                (.build
                                  (doto
                                    (Timestamp/newBuilder)
                                    (.setSeconds (.getEpochSecond i))
                                    (.setNanos (.getNano i))))))
                            ;;
                            (.setField data' field-descriptor (->pb v (.getMessageType field-descriptor)))))
               ;;
               :enum (let [enum-descriptor (.getEnumType field-descriptor)
                           enum-value-descriptor (.findValueByName enum-descriptor (name v))]
                       (.setField data' field-descriptor enum-value-descriptor))
               (.setField data' field-descriptor v))))
         b
         data)))))


(comment
  (println (str (->struct {:a 1 :b 2 :c [{:d 4 :e 5} 109 "jfqijoq"] :x-z true :y nil}))))
