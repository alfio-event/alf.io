
---
title: "Introduction"
linkTitle: "Introduction"
weight: 6
date: 2020-11-26
description: >
  Introduction about the extensions
---
# Alf.io extensions language

The extensions must be written in Javascript. However, we put some limitations on the functionalities of the language 
in order to prevent misuse or just to help you avoid making some mistakes. What we do is, before compiling your script, 
we verify that the code is legal. Then, also during compilation time, other checks are used to double verify the code.

# Limitations on loops

Loops such as `while` and `do` are not permitted. In case they are used, the script fails. Instead, you can use 
the `for`, `for/in` or `for/of` loops for any kind of iteration.

# Timeout handling

Sometimes the execution can take too long because of various reasons, therefore a timeout of 5 seconds is set for
each instruction. If it takes more than that, the script will be forcibly terminated.

# `with` statement

Usage of a `with` statement is forbidden. To avoid it, you can use a temporary variable. So, you can replace this:
```javascript
with (person) {
    console.log("Hello " + firstName + " "+ lastName);
}
```
with this:
```javascript
var p = person;
console.log("Hello " + p.firstName + " "+ p.lastName);
```
# Labeled statement

Labeled statements are rarely found, because usually function calls are used. They are also not permitted when writing 
the extensions. As mentioned above, `for`, `for/in` or `for/of` loops can be used instead.

# Functions limitations

Java functions can be called from the scripts, therefore we limit some harmful usage by applying sandboxing. Access to
`java.lang.System.exit()` and `getClass()` are disabled. In general, access to Java classes is not possible. However,
the standard objects (`Object`, `String`, `Number`, `Date`, etc.) can be used. In addition, we enable the use of
the following classes: `GSON`, [`SimpleHttpClient`](https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/extension/SimpleHttpClient.java),
 `HashMap`, [`ExtensionUtils`]( https://github.com/alfio-event/alf.io/blob/master/src/main/java/alfio/extension/ExtensionUtils.java) 
 and [`Logger`](https://logging.apache.org/log4j/2.x/log4j-api/apidocs/org/apache/logging/log4j/Logger.html).


# Function calls level limitation

Nesting more than one function call is not allowed. Below is an example of code that should ***not*** be used. 
```javascript
function executeScript(scriptEvent) {
    var a = 1;
    var b = 2;
    // first function call
    var result = addAndIncrement(a, b);
    return result;
}

function increment(x) {
    return x++;
}

function addAndIncrement(a, b) {
    // second function call from a previous function call -> exception
    return increment(a + b);
}
```
Instead, here is an example of legit function calls:
```javascript
function executeScript(scriptEvent) {
    var a = 1;
    var b = 2;
    // first function call
    var result = addAndIncrement(a, b);
    return result;
}

function increment(x) {
    return x++;
}

function addAndIncrement(a, b) {
    // no other function call -> OK
    return (a + b + 1);
}
```