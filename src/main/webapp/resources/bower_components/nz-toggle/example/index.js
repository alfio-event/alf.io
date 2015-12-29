var demo = angular.module('demo', ['nzToggle']);

demo.controller('mainController', function($scope) {

	angular.extend($scope, {
		count: 0,

		increment: increment
	});



	function increment(val) {
		console.log(val);
		$scope.count++;
		return true;
	}
});
