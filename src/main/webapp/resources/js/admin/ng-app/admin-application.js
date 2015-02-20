(function () {
    "use strict";

    var BASE_TEMPLATE_URL = "/admin/partials";
    var BASE_STATIC_URL = "/resources/angular-templates/admin/partials";
    var PAYMENT_PROXY_DESCRIPTIONS = {
        'STRIPE': 'Credit card payments',
        'ON_SITE': 'On site (cash) payment',
        'OFFLINE': 'Offline payment (bank transfer, invoice, etc.)'
    };
    var admin = angular.module('adminApplication', ['ui.bootstrap', 'ui.router', 'adminDirectives', 'adminServices', 'utilFilters', 'ngMessages', 'angularFileUpload', 'chart.js']);

    admin.config(function($stateProvider, $urlRouterProvider) {
        $urlRouterProvider.otherwise("/");
        $stateProvider
            .state('index', {
                url: "/",
                templateUrl: BASE_TEMPLATE_URL + "/index.html"
            })
            .state('index.new-organization', {
                url: "new-organization",
                views: {
                    "newOrganization": {
                        templateUrl: BASE_STATIC_URL + "/main/edit-organization.html",
                        controller: 'CreateOrganizationController'
                    }
                }
            })
            .state('index.new-user', {
                url: "users/new",
                views: {
                    "editUser": {
                        templateUrl: BASE_STATIC_URL + "/main/edit-user.html",
                        controller: 'EditUserController'
                    }
                }
            })
            .state('index.edit-user', {
                url: "users/:userId/edit",
                views: {
                    "editUser": {
                        templateUrl: BASE_STATIC_URL + "/main/edit-user.html",
                        controller: 'EditUserController'
                    }
                }
            })
            .state('events', {
                abstract: true,
                url: '/events',
                templateUrl: BASE_STATIC_URL + "/event/index.html"
            })
            .state('events.new', {
                url: '/new',
                templateUrl: BASE_STATIC_URL + "/event/edit-event.html",
                controller: 'CreateEventController'
            })
            .state('events.detail', {
                url: '/:eventName',
                templateUrl: BASE_STATIC_URL + '/event/detail.html',
                controller: 'EventDetailController'
            })
            .state('events.checkIn', {
            	url: '/:eventName/check-in',
            	templateUrl: BASE_STATIC_URL + '/event/check-in.html',
            	controller: 'EventCheckInController'
            })
            .state('events.sendInvitations', {
            	url: '/:eventName/c/:categoryId/send-invitation',
            	templateUrl: BASE_STATIC_URL + '/event/fragment/send-reserved-codes.html',
            	controller: 'SendInvitationsController'
            })
            .state('configuration', {
                url: '/configuration',
                templateUrl: BASE_STATIC_URL + '/configuration/index.html',
                controller: 'ConfigurationController'
            })
            .state('pending-reservations', {
                url: '/pending-reservations/:eventName/',
                templateUrl: BASE_STATIC_URL + '/pending-reservations/index.html',
                controller: 'PendingReservationsController'
            });

        var printLabel = function(val) {
            return val.label + ' ('+ val.value +')';
        };

        Chart.defaults.global.multiTooltipTemplate = function(val) {
            return printLabel(val);
        };
        Chart.defaults.global.tooltipTemplate = function(val) {
            return printLabel(val);
        };
        Chart.defaults.global.colours = [
            { // yellow
                fillColor: "rgba(253,180,92,0.2)",
                strokeColor: "rgba(253,180,92,1)",
                pointColor: "rgba(253,180,92,1)",
                pointStrokeColor: "#fff",
                pointHighlightFill: "#fff",
                pointHighlightStroke: "rgba(253,180,92,0.8)"
            },
            { // green
                fillColor: "rgba(70,191,189,0.2)",
                strokeColor: "rgba(70,191,189,1)",
                pointColor: "rgba(70,191,189,1)",
                pointStrokeColor: "#fff",
                pointHighlightFill: "#fff",
                pointHighlightStroke: "rgba(70,191,189,0.8)"
            },
            { // blue
                fillColor: "rgba(151,187,205,0.2)",
                strokeColor: "rgba(151,187,205,1)",
                pointColor: "rgba(151,187,205,1)",
                pointStrokeColor: "#fff",
                pointHighlightFill: "#fff",
                pointHighlightStroke: "rgba(151,187,205,0.8)"
            },
            { // light grey
                fillColor: "rgba(220,220,220,0.2)",
                strokeColor: "rgba(220,220,220,1)",
                pointColor: "rgba(220,220,220,1)",
                pointStrokeColor: "#fff",
                pointHighlightFill: "#fff",
                pointHighlightStroke: "rgba(220,220,220,0.8)"
            }

        ];
    });
    navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;

    var validationResultHandler = function(form, deferred) {
        return function(validationResult) {
            if(validationResult.errorCount > 0) {
                angular.forEach(validationResult.validationErrors, function(error) {
                    form.$setError(error.fieldName, error.message);
                });
                deferred.reject('invalid form');
            }
            deferred.resolve();
        };
    };

    var validationPerformer = function($q, validator, data, form) {
        var deferred = $q.defer();
        validator(data).success(validationResultHandler(form, deferred)).error(function(error) {
            deferred.reject(error);
        });
        return deferred.promise;
    };

    admin.controller('CreateOrganizationController', function($scope, $state, $rootScope, $q, OrganizationService) {
        $scope.organization = {};
        $scope.save = function(form, organization) {
            if(!form.$valid) {
                return;
            }
            validationPerformer($q, OrganizationService.checkOrganization, organization, form).then(function() {
                OrganizationService.createOrganization(organization).success(function() {
                    $rootScope.$emit('ReloadOrganizations', {});
                    $state.go('index');
                });
            }, angular.noop);
        };
        $scope.cancel = function() {
            $state.go('index');
        };
    });

    admin.controller('MenuController', function($scope) {
        $scope.menuCollapsed = true;
        $scope.toggleCollapse = function(currentStatus) {
            $scope.menuCollapsed = !currentStatus;
        };
    });

    admin.controller('EditUserController', function($scope, $state, $stateParams, $rootScope, $q, OrganizationService, UserService) {
        if(angular.isDefined($stateParams.userId)) {
            UserService.loadUser($stateParams.userId).success(function(result) {
                $scope.user = result;
            });
        }
        var organizations = [];
        $scope.user = {};
        $scope.organizations = [];
        OrganizationService.getAllOrganizations().success(function(result) {
            organizations = result;
            $scope.organizations = result;
        });

        $scope.save = function(form, user) {
            if(!form.$valid) {
                return;
            }

            var successFn = function() {
                $rootScope.$emit('ReloadUsers', {});
                $state.go('index');
            };

            validationPerformer($q, UserService.checkUser, user, form).then(function() {
                UserService.editUser(user).success(function(user) {
                    if(angular.isDefined(user.password)) {
                        UserService.showUserData(user).then(function() {
                            successFn();
                        });
                    } else {
                        successFn();
                    }

                });
            }, angular.noop);
        };

        $scope.cancel = function() {
            $state.go('index');
        };

    });

    var createCategory = function(sticky, $scope, expirationExtractor) {
        var lastCategory = _.last($scope.event.ticketCategories);
        var inceptionDate, notBefore;
        if(angular.isDefined(lastCategory)) {
            var lastExpiration = angular.isFunction(expirationExtractor) ? expirationExtractor(lastCategory) : lastCategory.expiration.date;
            inceptionDate = moment(lastExpiration).format('YYYY-MM-DD');
            notBefore = inceptionDate;
        } else {
            inceptionDate = moment().format('YYYY-MM-DD');
            notBefore = undefined;
        }

        return {
            inception: {
                date: inceptionDate
            },
            tokenGenerationRequested: false,
            expiration: {},
            sticky: sticky,
            notBefore: notBefore
        };

    };

    var createAndPushCategory = function(sticky, $scope, expirationExtractor) {
        $scope.event.ticketCategories.push(createCategory(sticky, $scope, expirationExtractor));
    };

    var initScopeForEventEditing = function ($scope, OrganizationService, PaymentProxyService, LocationService, $state) {
        $scope.organizations = {};

        OrganizationService.getAllOrganizations().success(function(result) {
            $scope.organizations = result;
        });

        PaymentProxyService.getAllProxies().success(function(result) {
            $scope.allowedPaymentProxies = _.map(result, function(p) {
                return {
                    id: p,
                    description: PAYMENT_PROXY_DESCRIPTIONS[p] || 'Unknown provider ('+p+')  Please check configuration'
                };
            });
        });

        $scope.addCategory = function() {
            createAndPushCategory(false, $scope);
        };

        $scope.canAddCategory = function(categories) {
            var remaining = _.foldl(categories, function(difference, category) {
                return difference - category.maxTickets;
            }, $scope.event.availableSeats);

            return remaining > 0 && _.every(categories, function(category) {
                return angular.isDefined(category.name) &&
                    angular.isDefined(category.maxTickets) &&
                    category.maxTickets > 0 &&
                    angular.isDefined(category.expiration.date);
            });
        };

        $scope.cancel = function() {
            $state.go('index');
        };

    };

    admin.controller('CreateEventController', function($scope, $state, $rootScope,
                                                       $q, OrganizationService, PaymentProxyService,
                                                       EventService, LocationService) {

        $scope.event = {
            freeOfCharge: false,
            begin: {},
            end: {}
        };
        initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, $state);
        $scope.event.ticketCategories = [];
        createAndPushCategory(true, $scope);

        $scope.save = function(form, event) {
            if(!form.$valid) {
                return;
            }
            validationPerformer($q, EventService.checkEvent, event, form).then(function() {
                EventService.createEvent(event).success(function() {
                    $state.go('index');
                });
            }, angular.noop);
        };

    });

    admin.controller('EventDetailController', function ($scope,
                                                        $stateParams,
                                                        OrganizationService,
                                                        PromoCodeService,
                                                        EventService,
                                                        LocationService,
                                                        $rootScope,
                                                        PaymentProxyService,
                                                        $state,
                                                        $log,
                                                        $q,
                                                        $window,
                                                        $modal) {
        var loadData = function() {
            $scope.loading = true;
            EventService.getEvent($stateParams.eventName).success(function(result) {
                $scope.event = result.event;
                $scope.organization = result.organization;
                $scope.validCategories = _.filter(result.event.ticketCategories, function(tc) {
                    return !tc.expired;
                });
                $scope.loading = false;
                $scope.loadingMap = true;
                LocationService.getMapUrl(result.event.latitude, result.event.longitude).success(function(mapUrl) {
                    $scope.event.geolocation = {
                        mapUrl: mapUrl,
                        timeZone: result.event.timeZone
                    };
                    $scope.loadingMap = false;
                });
                
                
                PromoCodeService.list(result.event.id).success(function(list) {
                	$scope.promocodes = list;
                	angular.forEach($scope.promocodes, function(v) {
                		(function(v) {
                			PromoCodeService.countUse(result.event.id, v.promoCode).then(function(val) {
                				v.useCount = parseInt(val.data, 10);
                			});
                		})(v);
                	});
                });
            });
        };
        loadData();
        initScopeForEventEditing($scope, OrganizationService, PaymentProxyService, LocationService, $state);
        $scope.evaluateCategoryStatusClass = function(index, category) {
            if(category.expired) {
                return 'category-expired';
            }
            return 'category-' + $rootScope.evaluateBarType(index);
        };

        $scope.evaluateClass = function(token) {
            switch(token.status) {
                case 'WAITING':
                    return 'bg-warning fa fa-cog fa-spin';
                case 'FREE':
                    return 'fa fa-qrcode';
                case 'TAKEN':
                    return 'bg-success fa fa-check';
                case 'CANCELLED':
                    return 'bg-default fa fa-eraser';
            }
        };

        $scope.isTokenViewCollapsed = function(category) {
            return !category.isTokenViewExpanded;
        };

        $scope.isTicketViewCollapsed = function(category) {
            return !category.isTicketViewExpanded;
        };

        $scope.toggleTokenViewCollapse = function(category) {
            category.isTokenViewExpanded = !category.isTokenViewExpanded;
        };

        $scope.toggleTicketViewCollapse = function(category) {
            category.isTicketViewExpanded = !category.isTicketViewExpanded;
        };

        $scope.evaluateTicketStatus = function(status) {
            var cls = 'fa ';

            switch(status) {
                case 'PENDING':
                    return cls + 'fa-warning text-warning';
                case 'ACQUIRED':
                case 'TO_BE_PAID':
                    return cls + 'fa-bookmark text-success';
                case 'CHECKED_IN':
                    return cls + 'fa-check-circle text-success';
                case 'CANCELLED':
                    return cls + 'fa-close text-danger';
            }

            return cls + 'fa-cog';
        };

        $scope.isPending = function(token) {
            return token.status === 'WAITING';
        };

        $scope.isReady = function(token) {
            return token.status === 'WAITING';
        };

        $scope.moveOrphans = function(srcCategory, targetCategoryId, eventId) {
            EventService.reallocateOrphans(srcCategory, targetCategoryId, eventId).success(function(result) {
                if(result === 'OK') {
                    loadData();
                }
            });
        };

        $scope.eventHeader = {};
        $scope.eventPrices = {};

        $scope.toggleEditHeader = function(editEventHeader) {
            $scope.editEventHeader = !editEventHeader;
        };

        $scope.toggleEditPrices = function(editPrices) {
            $scope.editPrices = !editPrices;
        };

        var validationErrorHandler = function(result, form, fieldsContainer) {
            return $q(function(resolve, reject) {
                if(result.data['errorCount'] == 0) {
                    resolve(result);
                } else {
                    form.$setValidity(false);
                    _.forEach(result.data.validationErrors, function(error) {
                        var field = fieldsContainer[error.fieldName];
                        if(angular.isDefined(field)) {
                            field.$setValidity('required', false);
                            field.$setTouched();
                        }
                    });
                    reject('validation error');
                }
            });
        };

        var errorHandler = function(error) {
            $log.error(error.data);
            alert(error.data);
        };

        $scope.saveEventHeader = function(form, header) {
            if(!form.$valid) {
                return;
            }
            EventService.updateEventHeader(header).then(function(result) {
                validationErrorHandler(result, form, form.editEventHeader).then(function(result) {
                    $scope.editEventHeader = false;
                    loadData();
                });
            }, errorHandler);
        };

        $scope.saveEventPrices = function(form, eventPrices, organizationId) {
            if(!form.$valid) {
                return;
            }
            var obj = {'organizationId':organizationId};
            angular.extend(obj, eventPrices);
            EventService.updateEventPrices(obj).then(function(result) {
                validationErrorHandler(result, form, form.editPrices).then(function(result) {
                    $scope.editPrices = false;
                    loadData();
                });
            }, errorHandler);
        };

        var openCategoryDialog = function(category, event) {
            var editCategory = $modal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/edit-category-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                    $scope.ticketCategory = category;
                    $scope.event = event;
                    $scope.editMode = true;
                    $scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, category, event) {
                        if(!form.$valid) {
                            return;
                        }
                        EventService.saveTicketCategory(event, category).then(function(result) {
                            validationErrorHandler(result, form, form.ticketCategory).then(function() {
                                $scope.$close(true);
                            });
                        }, errorHandler);
                    };
                }
            });
            return editCategory.result;
        };

        $scope.addCategory = function(event) {
            openCategoryDialog(createCategory(true, $scope, function(obj) {return obj.formattedExpiration}), event).then(function() {
                loadData();
            });
        };

        $scope.toggleLocking = function(ticket, category) {
            EventService.toggleTicketLocking(ticket, category).then(function() {
                loadData();
            });
        };

        $scope.editCategory = function(category, event) {
            var inception = moment(category.formattedInception);
            var expiration = moment(category.formattedExpiration);
            var categoryObj = {
                id: category.id,
                name: category.name,
                price: category.actualPrice,
                description: category.description,
                maxTickets: category.maxTickets,
                inception: {
                    date: inception.format('YYYY-MM-DD'),
                    time: inception.format('HH:mm')
                },
                expiration: {
                    date: expiration.format('YYYY-MM-DD'),
                    time: expiration.format('HH:mm')
                },
                tokenGenerationRequested: category.accessRestricted,
                sticky: false
            };

            openCategoryDialog(categoryObj, event).then(function() {
                loadData();
            });
        };

        var getPendingPayments = function() {
            EventService.getPendingPayments($stateParams.eventName).success(function(data) {
                $scope.pendingReservations = data;
            });
        };

        getPendingPayments();
        $scope.registerPayment = function(eventName, id) {
            $scope.loading = true;
            EventService.registerPayment(eventName, id).success(function() {
                loadData();
                getPendingPayments();
            }).error(function() {
                $scope.loading = false;
            });
        };
        $scope.deletePayment = function(eventName, id) {
            $scope.loading = true;
            EventService.cancelPayment(eventName, id).success(function() {
                loadData();
                getPendingPayments();
            }).error(function() {
                $scope.loading = false;
            });
        };
        
        //
        
        $scope.deletePromocode = function(promocode) {
        	if($window.confirm('Delete promo code ' + promocode.promoCode + '?')) {
        		PromoCodeService.remove($scope.event.id, promocode.promoCode).then(loadData, errorHandler);
        	}
        };
        
        $scope.disablePromocode = function(promocode) {
        	if($window.confirm('Disable promo code ' + promocode.promoCode + '?')) {
        		PromoCodeService.disable($scope.event.id, promocode.promoCode).then(loadData, errorHandler);
        	}
        };
        
        $scope.addPromoCode = function(event) {
        	$modal.open({
                size:'lg',
                templateUrl:BASE_STATIC_URL + '/event/fragment/edit-promo-code-modal.html',
                backdrop: 'static',
                controller: function($scope) {
                	
                	$scope.event = event;
                	
                	var now = moment();
                	var eventBegin = moment(event.formattedBegin);
                	
                	$scope.promocode = {discountType :'PERCENTAGE', start : {date: now.format('YYYY-MM-DD'), time: now.format('HH:mm')}, end: {date: eventBegin.format('YYYY-MM-DD'), time: eventBegin.format('HH:mm')}};
                	
                	$scope.$watch('promocode.promoCode', function(newVal) {
                		if(newVal) {
                			$scope.promocode.promoCode = newVal.toUpperCase();
                		}
                	});
                	
                	$scope.cancel = function() {
                        $scope.$dismiss('canceled');
                    };
                    $scope.update = function(form, promocode, event) {
                        if(!form.$valid) {
                            return;
                        }
                        $scope.$close(true);
                        
                        PromoCodeService.add(event.id, promocode).then(function(result) {
                            validationErrorHandler(result, form, form.promocode).then(function() {
                                $scope.$close(true);
                            });
                        }, errorHandler).then(loadData);
                    };
                }
            });
        };

    });

    admin.controller('SendInvitationsController', function($scope, $stateParams, $state, EventService, $upload, $log) {
        $scope.eventName = $stateParams.eventName;
        $scope.categoryId = $stateParams.categoryId;

        $scope.sendCodes = function(data) {
            EventService.sendCodesByEmail($stateParams.eventName, $stateParams.categoryId, data).success(function() {
                alert('Codes have been successfully sent');
                $state.go('events.detail', {eventName: $stateParams.eventName});
            }).error(function(e) {
                alert(e.data);
            });
        };

        $scope.uploadFile = function(files) {
            $scope.results = [];
            $scope.upload = $upload.upload({
                url: '/admin/api/events/'+$stateParams.eventName+'/categories/'+$stateParams.categoryId+'/link-codes',
                method: 'POST',
                file: files[0]
            }).progress(function(evt) {
                $log.info('progress: ' + parseInt(100.0 * evt.loaded / evt.total) + '% file : '+ evt.config.file.name);
            }).success(function(data/*, status, headers, config*/) {
                $scope.results = data;
            }).error(function(e) {
                alert(e.data);
            });
        };
    });
    
    admin.controller('EventCheckInController', function($scope, $stateParams, $timeout, $log, EventService, CheckInService) {
    	
    	
    	navigator.getUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;
    	
    	$scope.videos = [];
    	$scope.stream = null;
    	
    	var timeoutPromise = null;
    	var worker = new Worker("../resources/js/jsqrcode/decode-worker.js");
    	
		worker.addEventListener('message', function(message) {
			var result = message.data;
			if(result === 'error decoding QR Code') {
				$log.debug('error decoding qr code');
			} else {
				$scope.$apply(function() {
					$scope.scanning.visible = false;
					$scope.scanning.ticket.code = result;
					
					CheckInService.getTicket($scope.event.id, result).success(function(result) {
						$scope.scanning.scannedTicketInfo = result;
					});
				});
			}
		}, false);
		
		function endVideoStream() {
			if (!!$scope.stream) {
    			$scope.stream.stop();
    		}
		}
		
		$scope.$on('$destroy', function() {
			worker.terminate();
			endVideoStream();
		});
    	
    	function stopScanning() {
    		endVideoStream();
    		$scope.scanning.visible = false; 
    		$timeout.cancel(timeoutPromise);
    	}
    	
    	
    	$scope.stopScanning = stopScanning;
    	
    	$scope.$on('$destroy', stopScanning);
    	
    	function captureFrame() {
    		if($scope.scanning.visible) {
    			$log.debug('try to capture frame');
	    		try {
	    			var videoElement = document.getElementById('checkInVideoElement');
					var canvas = document.getElementById("checkInImageCanvas");
					canvas.height = videoElement.videoHeight;
					canvas.width = videoElement.videoWidth;
					
					canvas.getContext("2d").drawImage(videoElement, 0, 0);
					var imageData = canvas.getContext("2d").getImageData(0,0,canvas.width, canvas.height);
					worker.postMessage(imageData);
				} catch(e) {
					$log.debug('error', e)
				}
    		}
			
			timeoutPromise = $timeout(function() {
				captureFrame();
			}, 250);
    	}
    	
    	
    	$scope.selectSource = function(source) {
    		if(source == undefined) {
    			return;
    		}

    		endVideoStream();
    		var videoElement = document.getElementById('checkInVideoElement');
    		videoElement.src = null;
    		
    		
    		var constraint = {video: {optional: [{sourceId: source.source.id}]}};
    		
    		navigator.getUserMedia(constraint, function(stream) {
    			$scope.stream = stream; // make stream available to console
    			videoElement.src = window.URL.createObjectURL(stream);
    			videoElement.play();
    			$timeout.cancel(timeoutPromise);
    			captureFrame();
    		}, function() {
    			alert('error while loading camera');
    			$timeout.cancel(timeoutPromise);
    		});
    	};
    	
    	MediaStreamTrack.getSources(function(sources) {
    		var videos = [];
    		angular.forEach(sources, function(v,i) {
    			if(v.kind === 'video') {
    				videos.push({ source: v, label: (v.label || 'camera ' + i)});
    			}
    		});
    		$scope.$apply(function() {
    			$scope.videos = videos;
    		});
    	});
    	
    	EventService.getEvent($stateParams.eventName).success(function(result) {
    		$scope.event = result.event;
    		CheckInService.findAllTickets(result.event.id).success(function(tickets) {
    			$scope.tickets = tickets;
    		});
    	});
    	
    	$scope.toBeCheckedIn = function(ticket, idx) {
    		return  ['TO_BE_PAID', 'ACQUIRED'].indexOf(ticket.status) >= 0;
    	};
    	
    	$scope.reloadTickets = function() {
    		CheckInService.findAllTickets($scope.event.id).success(function(tickets) {
    			$scope.tickets = tickets;
    		});
    	};
    	
    	$scope.checkIn = function(ticket) {
    		CheckInService.checkIn($scope.event.id, ticket).success(function(result) {
    			if(result.status === 'SUCCESS') {
    				ticket.code = null;
    				$scope.reloadTickets();
    			}
    			$scope.checkInResult = result;
    		});
    	};
    	
    	$scope.confirmPayment = function(ticket) {
    		CheckInService.confirmPayment($scope.event.id, ticket).success(function(result) {
    			if(result.status) {
    				$scope.confirmPaymentResult= null;
    				$scope.checkIn(ticket);
    			} else {
    				$scope.confirmPaymentResult= result;
    			}
    		});
    	};
    	
    	$scope.resetForm = function(ticket) {
    		ticket.code = null;
    		$scope.checkInResult = null;
    		$scope.confirmPaymentResult = null;
    		$scope.scanning.scannedTicketInfo = null;
    	};
    });

    admin.controller('MessageBarController', function($scope, $rootScope) {
        $rootScope.$on('Message', function(m) {
            $scope.message = m;
        });
    });

    admin.controller('ConfigurationController', function($scope, ConfigurationService, $rootScope) {
        $scope.loading = true;
        var populateScope = function(result) {
            $scope.settings = result;
            $scope.general = {
                settings: result['GENERAL']
            };
            $scope.mail = {
                settings: _.filter(result['MAIL'], function(e) {return e.key !== 'MAILER_TYPE';}),
                type: _.find(result['MAIL'], function(e) {return e.configurationKey === 'MAILER_TYPE';}),
                maxEmailPerCycle: _.find(result['MAIL'], function(e) {return e.configurationKey === 'MAX_EMAIL_PER_CYCLE';}),
                mailReplyTo: _.find(result['MAIL'], function(e) {return e.configurationKey === 'MAIL_REPLY_TO';})
            };
            $scope.payment = {
                settings: result['PAYMENT']
            };
            $scope.loading = false;
        };
        var loadAll = function() {
            $scope.loading = true;
            ConfigurationService.loadAll().success(function (result) {
                populateScope(result);
            });
        };
        loadAll();

        $scope.saveSettings = function(frm, settings) {
            if(!frm.$valid) {
                return;
            }
            $scope.loading = true;
            ConfigurationService.bulkUpdate(settings).then(function(result) {
                populateScope(result.data);
            }, function(e) {
                alert(e.data);
                $scope.loading = false;
            });
        };
        
        $scope.configurationChange = function(conf) {
            if(!conf.value) {
                return;
            }
            $scope.loading = true;
            ConfigurationService.update(conf).success(function(result) {
                $scope.settings = result;
                $scope.loading = false;
            });
        };

        $rootScope.$on('ReloadSettings', function() {
            loadAll();
        });
    });

    admin.controller('PendingReservationsController', function($scope, EventService, $stateParams, $upload, $log, $window) {
        var getPendingPayments = function() {
            EventService.getPendingPayments($stateParams.eventName).success(function(data) {
                $scope.pendingReservations = data;
                $scope.loading = false;
            });
        };

        $scope.eventName = $stateParams.eventName;
        $scope.uploadFiles = function(files) {
            $scope.results = [];
            $scope.upload = $upload.upload({
                url: '/admin/api/events/'+$stateParams.eventName+'/pending-payments/bulk-confirmation',
                method: 'POST',
                file: files[0]
            }).progress(function(evt) {
                $log.info('progress: ' + parseInt(100.0 * evt.loaded / evt.total) + '% file :'+ evt.config.file.name);
            }).success(function(data, status, headers, config) {
                $scope.results = data;
                getPendingPayments();
            });
        };

        getPendingPayments();
        $scope.registerPayment = function(eventName, id) {
            $scope.loading = true;
            EventService.registerPayment(eventName, id).success(function() {
                getPendingPayments();
            }).error(function() {
                $scope.loading = false;
            });
        };
        $scope.deletePayment = function(eventName, id) {
            if(!$window.confirm('Do you really want to delete this reservation?')) {
                return;
            }
            $scope.loading = true;
            EventService.cancelPayment(eventName, id).success(function() {
                getPendingPayments();
            }).error(function() {
                $scope.loading = false;
            });
        };
    });

    admin.run(function($rootScope, PriceCalculator) {
        var calculateNetPrice = function(event) {
            if(isNaN(event.regularPrice) || isNaN(event.vat)) {
                return numeral(0.0);
            }
            if(!event.vatIncluded) {
                return numeral(event.regularPrice);
            }
            return numeral(event.regularPrice).divide(numeral(1).add(numeral(event.vat).divide(100)));
        };

        $rootScope.evaluateBarType = function(index) {
            var barClasses = ['danger', 'warning', 'info', 'success'];
            if(index < barClasses.length) {
                return barClasses[index];
            }
            return index % 2 == 0 ? 'info' : 'success';
        };

        $rootScope.calcBarValue = PriceCalculator.calcBarValue;

        $rootScope.calcCategoryPricePercent = PriceCalculator.calcCategoryPricePercent;

        $rootScope.calcCategoryPrice = PriceCalculator.calcCategoryPrice; 

        $rootScope.calcPercentage = PriceCalculator.calcPercentage;

        $rootScope.applyPercentage = PriceCalculator.applyPercentage;

        $rootScope.calculateTotalPrice = PriceCalculator.calculateTotalPrice;
    });

})();
