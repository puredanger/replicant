# replicant

A sketch of using the Clojure socket server facility to create a repl suitable
for tooling use. This is not a published library that you should use directly - 
feel free to steal and modify any code you see here.

## Usage

The goal here is for a tool (IDE) to create a REPL for a user where the tool is able
to snoop on the user's REPL context (for example, dynamic vars like *ns*).

### Start the JVM with a data tooling REPL server

First, we need to start a JVM - this should use the project's classpath and settings
but may also need to include access to any extra code needed by the tooling repl 
(like the stuff in this project). You will also need to instruct the Clojure runtime
to start a socket server that the tool will connect to as a client with a system property:

```
-Dclojure.server.datarepl="{:port 5555 :accept 'replicant.util/data-repl}"
```

The `data-repl` accept function will be called in this JVM when you connect as a 
client on port 5555 to this JVM. The data-repl is just a normal repl with modifications
to capture \*out\* and \*err\* and to turn off the REPL prompt.

The response from any expression sent to the data-repl will be a map that contains
the keys :result (if successful), :exception (if thrown, in map form), :out, and :err.

### Start user REPL server

The tool must then connect to the running JVM on the specified port (5555).
The tool is then connected as a client to the user's application. From within this connection
we need to set up a state container for the user's environment bindings. It can then 
evaulate arbitrary expressions in the context of the user's environment.

```clojure
  (let [bindings (atom {})          ;; shared atom to stash user's bindings
        repl-name (gensym "repl")   ;; generate repl server name
        server ^SocketServer (server/start-server
                                {:name   repl-name
                                 :port   0   ;; pick a free port
                                 :accept 'replicant.util/repl
                                 :args   [:eval (partial user-eval bindings)]})
        repl-port (.getLocalPort server)]

    ;; do stuff below in this context
  )
```

This code creates an atom to stash user bindings, then starts a new repl server with
a special eval function, `user-eval`, which looks like this:

```clojure
(defn user-eval
  [binding-atom form]
  (let [result (eval form)]
    (swap! binding-atom (get-thread-bindings))
    result))
```

`user-eval` does one special thing - after you evaluate a form, it stashes the user's
current bindings in the specified atom. In our case, this will be an atom that the 
tooling repl has access to.

### Connect user REPL connection

The tool should then create a normal socket REPL connection to the repl-port
retrieved above. When they do so they will be using `user-eval` and their bindings
will be saved off after every REPL evaluation in the `binding-atom`. 

### Evaluate in user's environment

Now that the user's state is being saved off, the tooling repl (the first connection)
can evaluate arbitrary expressions (like \*ns\*) in the context of the first
tooling REPL connection using something like `with-user-bindings`:

```
(defmacro with-user-bindings
  [binding-atom & body]
  `(with-bindings ~@binding-atom body))
```




## License

Copyright © 2015 Alex Miller

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
