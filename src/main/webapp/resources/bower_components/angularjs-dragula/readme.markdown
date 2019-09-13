[![logo.png][3]][2]

> Drag and drop so simple it hurts

Official Angular wrapper for [`dragula`][4].

# Demo

[![demo.png][1]][2]

Try out the [demo][2]!

# Install

You can get it on npm.

```shell
npm install angularjs-dragula --save
```

Or bower, too.

```shell
bower install angularjs-dragula --save
```

# Setup

You'll need to pass in `angularDragula` to your module when booting up your application. `angularjs-dragula` takes your `angular` instance and uses it to register its own module, service, and directive.

```js
var angular = require('angular');
var angularDragula = require('angularjs-dragula');

var app = angular.module('my-app', [angularDragula(angular)]);
```

# Usage

This package isn't very different from `dragula` itself. I'll mark the differences here, but please refer to the documentation for [`dragula`][4] if you need to learn more about `dragula` itself.

## Directive

There's a `dragula` directive _[(as seen in the demo)][2]_ that allows you to group containers together, as long as they belong to the same scope. That grouping of containers is called a `bag`.

```html
<div dragula='"bag-one"'></div>
<div dragula='"bag-one"'></div>
<div dragula='"bag-two"'></div>
```

### `dragula-scope`

`ng-repeat` creates a new isolate scope, which can sometimes cause issues with dragging between a bag with multiple containers. To avoid this you can pass in the scope you want the bag to be stored on _(and fire events on)_ by setting the `dragula-scope` directive on the bag element.

```html
<ul ng-controller="ItemsController">
  <li ng-repeat="item in items" dragula='"bag-one"' dragula-scope="$parent"></li>
</ul>
```

### `dragula-model`

If your `ng-repeat` is compiled from array, you may wish to have it synced. For that purpose you need to provide model by setting the `dragula-model` attribute on the bag element

```html
<ul ng-controller="ItemsController">
  <li ng-repeat="item in items" dragula='"bag-one"' dragula-model="items"></li>
</ul>
```

The standard `drop` event is fired before the model is synced. For that purpose you need to use the `drop-model` event. The same behavior exists in the `remove` event. Therefore is the `remove-model` event. Further details are available under `Events`

### `drake` options

If you need to configure the `drake` _(there's only one `drake` per `bag`)_, you'll have to use the `dragulaService`.

```js
app.controller('ExampleCtrl', ['$scope', 'dragulaService',
  function ($scope, dragulaService) {
    dragulaService.options($scope, 'third-bag', {
      removeOnSpill: true
    });
  }
]);
```

## Events

Whenever a `drake` instance created with the `dragula` directive emits an event, that event is replicated on the Angular `$scope` where the `drake` has an associated `bag`, and prefixed with the `name` on its `bag`.

```html
<div dragula='"evented-bag"'></div>
```

```js
app.controller('ExampleCtrl', ['$scope', function ($scope) {
  $scope
    .$on('evented-bag.over', function (e, el) {
      el.addClass('over');
    })
    .$on('evented-bag.out', function (e, el) {
      el.removeClass('over');
    });
]);
```

Note that these derived events don't expose the DOM elements directly. The elements get wrapped in `angular.element` calls.

## Special Events for angularjs-dragula

| Event Name |      Listener Arguments      |  Event Description |
| :-------------: |:-------------:| -----|
| drop-model | el, target, source | same as normal drop, but model was synced, just available with the use of dragula-model |
| remove-model | el, container | same as normal remove, but model was synced, just available with the use of dragula-model |

## `dragulaService`

This service exposes a few different methods with which you can interact with `dragula` in the Angular way.

### `dragulaService.add(scope, name, drake)`

Creates a `bag` scoped under `scope` and identified by `name`. You should provide the entire `drake` instance. Typically, the directive takes care of this step.

### `dragulaService.options(scope, name, options)`

Sets the `options` used to instantiate a `drake`. Refer to the documentation for [`dragula`][4] to learn more about the `options` themselves.

### `dragulaService.find(scope, name)`

Returns the `bag` for a `drake` instance. Contains the following properties.

- `name` is the name that identifies the bag under `scope`
- `drake` is the raw `drake` instance itself

### `dragulaService.destroy(scope, name)`

Destroys a `drake` instance named `name` scoped under `scope`.

# License

MIT

[1]: https://github.com/bevacqua/angularjs-dragula/blob/master/resources/demo.png
[2]: http://bevacqua.github.io/angularjs-dragula/
[3]: https://github.com/bevacqua/angularjs-dragula/blob/master/resources/logo.png
[4]: https://github.com/bevacqua/dragula
