'use strict';

var app = angular.module('angularjs-dragula-example', [angularDragula(angular)]);

app.controller('ExampleCtrl', ['$scope', function ($scope) {
  $scope.$on('second-bag.drag', function (e, el) {
    el.removeClass('ex-moved');
  });

  $scope.$on('second-bag.drop', function (e, el) {
    el.addClass('ex-moved');
  });

  $scope.$on('second-bag.over', function (e, el, container) {
    container.addClass('ex-over');
  });

  $scope.$on('second-bag.out', function (e, el, container) {
    container.removeClass('ex-over');
  });
}]);

app.controller('AnotherExampleCtrl', ['$scope', 'dragulaService',
  function ($scope, dragulaService) {
    dragulaService.options($scope, 'third-bag', {
      removeOnSpill: true
    });
  }
]);

app.controller('SuchExampleCtrl', ['$scope', 'dragulaService',
  function ($scope, dragulaService) {
    dragulaService.options($scope, 'fourth-bag', {
      revertOnSpill: true
    });
  }
]);

app.controller('VeryExampleCtrl', ['$scope', 'dragulaService',
  function ($scope, dragulaService) {
    dragulaService.options($scope, 'fifth-bag', {
      copy: true
    });
  }
]);

app.controller('MuchExampleCtrl', ['$scope', 'dragulaService',
  function ($scope, dragulaService) {
    dragulaService.options($scope, 'sixth-bag', {
      moves: function (el, container, handle) {
        return handle.classList.contains('handle');
      }
    });
  }
]);

app.controller('WowExampleCtrl', ['$scope', '$timeout',
  function ($scope, $timeout) {
    $scope.onclick = onclick;

    function onclick () {
      $scope.clicked = true;
      $timeout(function offclick () {
        $scope.clicked = false;
      }, 2000);
    }
  }
]);

app.controller('RepeatCtrl', ['$scope',
  function ($scope) {
    $scope.many = ['The', 'possibilities', 'are', 'endless!'];
    $scope.many2 = ['Explore', 'them'];
  }
]);

app.controller('NestedRepeatCtrl', ['$scope',
  function ($scope) {
    $scope.groups = [
      {
        name: 'Group A',
        items: [{name: 'Item A'},{name: 'Item B'},{name: 'Item C'},{name: 'Item D'}]
      },
      {
        name: 'Group B',
        items: [{name: 'Item 1'},{name: 'Item 2'},{name: 'Item 3'},{name: 'Item 4'}]
      }
    ];

    $scope.$on('nested-bag.drop', function(e, el, container, source) {
      console.log(container.scope());
    });
  }
])
