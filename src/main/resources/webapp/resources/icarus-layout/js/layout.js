/** 
 * PrimeFaces Icarus Layout
 */
PrimeFaces.widget.Icarus = PrimeFaces.widget.BaseWidget.extend({
    
    init: function(cfg) {
        this._super(cfg);
        this.wrapper = $(document.body).children('.wrapper');
        this.menubar = $('#sidebar-wrapper');
        this.sidebarNav = this.menubar.children('.sidebar-nav');
        this.menubarElement = this.menubar.get(0);
        this.menubarContainer = this.menubar.find('ul.sidebar-nav-container');
        this.slimToggleButton = $('#slim-menu-button');
        this.content = $('#main-wrapper');
        this.menulinks = this.menubarContainer.find('a.menuLink');
        this.expandedMenuitems = this.expandedMenuitems || [];
        this.focusedItem = null;
        this.focusedTopItem = null;

        this.restoreMenuState();
        this.bindEvents();
    },
            
    toggleMenu: function() {
        if(this.isDesktop()) {
            this.wrapper.toggleClass('slim-menu');
            this.menubar.removeClass('normalize-menu');
        }
        else {
            this.menubar.toggleClass('normalize-menu');
            
            if(this.menubar.hasClass('normalize-menu'))
                this.wrapper.removeClass('slim-menu');
            else
                this.wrapper.addClass('slim-menu');
        }
        
        this.transitionControl();
        $(window).trigger('resize');
    },
        
    bindEvents: function() {        
        var $this = this;
        
        this.sidebarNav.nanoScroller({flash: true});
        
        this.slimToggleButton.off('click.toggleButton').on('click.toggleButton', function(e) {
            $this.toggleMenu();
            e.preventDefault();
        });
        
        this.menulinks.off('click.menuLinks').on('click.menuLinks',function(e) {
            var menuitemLink = $(this),
            menuitem = menuitemLink.parent(),
            hasSubmenuContainer = menuitemLink.next().is('ul'),
            isActive = menuitem.hasClass('active-menu-parent');
            
            if($this.menubar.width() < 60) {
                $this.toggleMenu();
                
                if(!isActive) {
                    $this.activateMenuitem(menuitem);
                }
            }
            else if(hasSubmenuContainer) {
                if(isActive)
                    $this.deactivateMenuitem(menuitem);
                else
                    $this.activateMenuitem(menuitem);
            }
            else if(!isActive) {
                $this.activateMenuitem(menuitem);
            }

            if(hasSubmenuContainer) {
                e.preventDefault();
            }

            $this.saveMenuState();    
            
            setTimeout(function() {
                $(".nano").nanoScroller();
            }, 750);
        });
        
        //remove transitions on IOS
        if(this.isIOS()) {
            this.menubar.find('a').addClass('notransition');
        }
        
        //workaround for firefox bug of not resetting scrolltop
        if(navigator.userAgent.toLowerCase().indexOf('firefox') > -1) {
            $(window).off('resize.layout').on('resize.layout', function() {
                $this.menubarElement.scrollTop = 0;
            });
        }
        
        $(document.body).off('click.layoutBody').on('click.layoutBody', function(e) {
            if(!$this.topbarLinkClick && $this.topbarItems) {
                $this.topbarItems.filter('.active').removeClass('active').children().removeClass('active-link');
                $this.profileImage.parent().removeClass('active');
            }
            $this.topbarLinkClick = false;
            
        });
        
        $(function() {
            $this.topbarItems = $('#top-bar').find('> .top-menu > .top-bar-icon');
            $this.topbarLinks = $this.topbarItems.find('> a');
            $this.topbarLinks.off('click.topbarLink').on('click.topbarLink', function(e) {
                $this.topbarLinkClick = true;
                var link = $(this),
                item = link.parent(),
                submenu = item.children('ul');
                
                item.siblings('.active').removeClass('active');
                $this.profileImage.parent().removeClass('active');
                
                if(submenu.length) {
                    submenu.addClass('');
                    item.toggleClass('active');
                    link.toggleClass('active-link');
                    e.preventDefault();
                }
            });
            
            $this.profileImage = $('#profile-image');
            $this.profileImageMobile = $('#profile-image-mobile');
            
            $this.profileImage.off('click.profileImage').on('click.profileImage', function(e) {
                $this.topbarLinkClick = true;
                var link = $(this);
                
                $this.topbarItems.filter('.active').removeClass('active').children().removeClass('active-link');
                
                link.parent().toggleClass('active');
                e.preventDefault();
            });
            
            $this.profileImageMobile.off('click.profileImageMobile').on('click.profileImageMobile', function(e) {
                $this.topbarLinkClick = true;
                var link = $(this);
                
                $this.topbarItems.filter('.active').removeClass('active').children().removeClass('active-link');
                
                link.parent().toggleClass('active');
                e.preventDefault();
            });
        });
    },
    
    deactivateSiblings: function(menuitem) {
        var activeSiblings = this.findActiveSiblings(menuitem),
        $this = this;
        
        for(var i = 0; i< activeSiblings.length; i++) {
            var activeSibling = activeSiblings[i];
            activeSibling.removeClass('active-menu-parent');
            this.removeMenuitem(activeSibling);

            activeSibling.find('ul.active-menu').slideUp(300, function() {
                $(this).removeClass('active-menu').removeAttr('style');
            });
            activeSibling.find('a.active-menu').removeClass('active-menu');
            activeSibling.find('li.active-menu-parent').each(function() {
                var menuitem = $(this);
                menuitem.removeClass('active-menu-parent');
                $this.removeMenuitem(menuitem);
            });
        }
    },
    
    activateMenuitem: function(menuitem) {
        this.deactivateSiblings(menuitem);
        menuitem.addClass('active-menu-parent').children('.menuLink').addClass('active-menu').next('ul').slideDown(300, function() {
            $(this).addClass('active-menu').removeAttr('style');
        });
        this.addMenuitem(menuitem.attr('id'));
    },
    
    deactivateMenuitem: function(menuitem) {
        menuitem.removeClass('active-menu-parent').children('.menuLink').removeClass('active-menu').next('ul').slideUp(300, function() {
            $(this).removeClass('active-menu').removeAttr('style');
        });
        this.removeMenuitem(menuitem);
    },
    
    findActiveSiblings: function(menuitem) {
        var $this = this,
        siblings = menuitem.siblings('li'),
        activeSiblings = [];
            
        siblings.each(function () {
            if ($.inArray($(this).attr('id'), $this.expandedMenuitems) !== -1 || $(this).hasClass('active-menu-parent')) {
                activeSiblings.push($(this));
            }
        });

        return activeSiblings;
    },
     
    restoreMenuState: function () {
        var menucookie = $.cookie('icarus_activemenuitem');
        if (menucookie) {
            this.expandedMenuitems = menucookie.split(',');
            for (var i = 0; i < this.expandedMenuitems.length; i++) {
                var id = this.expandedMenuitems[i];
                if (id) {
                    var menuitem = $("#" + this.expandedMenuitems[i].replace(/:/g, "\\:"));
                    menuitem.addClass('active-menu-parent');
                    menuitem.children('a').addClass('active-menu');
                    menuitem.children('ul').addClass('active-menu');
                }
            }
        }
    },
    
    removeMenuitem: function (menuitem) {
        var id = menuitem.attr('id');
        this.expandedMenuitems = $.grep(this.expandedMenuitems, function (value) {
            return value !== id;
        });
        
        var submenu = menuitem.children('ul.sidebar-submenu-container');
        if(submenu && submenu.length) {
            var activeMenu = submenu.children('.active-menu-parent');
            if(activeMenu && activeMenu.length) {
                activeMenu.removeClass('active-menu-parent');
                activeMenu.children('a,ul').removeClass('active-menu');
                this.removeMenuitem(activeMenu);
            }
        }
    },
    
    addMenuitem: function (id) {
        if ($.inArray(id, this.expandedMenuitems) === -1) {
            this.expandedMenuitems.push(id);
        }
    },
    
    saveMenuState: function() {
        $.cookie('icarus_activemenuitem', this.expandedMenuitems.join(','), {path:'/'});
    },
    
    clearMenuState: function() {
        $.removeCookie('icarus_activemenuitem', {path:'/'});
    },
    
    isIOS: function() {
        return ( navigator.userAgent.match(/(iPad|iPhone|iPod)/g) ? true : false );
    },
    
    closeMenu: function() {
        this.menubarContainer.find('.sidebar-submenu-container.active-menu').hide().removeClass('active-menu');
        this.menubarContainer.find('a.active-menu,li.active-menu-parent').removeClass('active-menu active-menu-parent');

        var nano = $(".nano");
        if(nano && nano.length) {
            $(".nano").nanoScroller({ stop: true });
        }
    },
    
    isTablet: function() {
        var width = window.innerWidth;
        return width <= 1024 && width > 640;
    },
    
    isDesktop: function() {
        return window.innerWidth > 1024;
    },
    
    isMobile: function() {
        return window.innerWidth <= 640;
    },
    
    transitionControl: function() {
        var $this = this;
        
        if(!this.isMobile()) {
            this.menubar.addClass('wrapperTransition');
            setTimeout(function(){
                $this.menubar.removeClass('wrapperTransition');
            },301);
        }
    }
    
});

/*!
 * jQuery Cookie Plugin v1.4.1
 * https://github.com/carhartl/jquery-cookie
 *
 * Copyright 2006, 2014 Klaus Hartl
 * Released under the MIT license
 */
(function (factory) {
	if (typeof define === 'function' && define.amd) {
		// AMD (Register as an anonymous module)
		define(['jquery'], factory);
	} else if (typeof exports === 'object') {
		// Node/CommonJS
		module.exports = factory(require('jquery'));
	} else {
		// Browser globals
		factory(jQuery);
	}
}(function ($) {

	var pluses = /\+/g;

	function encode(s) {
		return config.raw ? s : encodeURIComponent(s);
	}

	function decode(s) {
		return config.raw ? s : decodeURIComponent(s);
	}

	function stringifyCookieValue(value) {
		return encode(config.json ? JSON.stringify(value) : String(value));
	}

	function parseCookieValue(s) {
		if (s.indexOf('"') === 0) {
			// This is a quoted cookie as according to RFC2068, unescape...
			s = s.slice(1, -1).replace(/\\"/g, '"').replace(/\\\\/g, '\\');
		}

		try {
			// Replace server-side written pluses with spaces.
			// If we can't decode the cookie, ignore it, it's unusable.
			// If we can't parse the cookie, ignore it, it's unusable.
			s = decodeURIComponent(s.replace(pluses, ' '));
			return config.json ? JSON.parse(s) : s;
		} catch(e) {}
	}

	function read(s, converter) {
		var value = config.raw ? s : parseCookieValue(s);
		return $.isFunction(converter) ? converter(value) : value;
	}

	var config = $.cookie = function (key, value, options) {

		// Write

		if (arguments.length > 1 && !$.isFunction(value)) {
			options = $.extend({}, config.defaults, options);

			if (typeof options.expires === 'number') {
				var days = options.expires, t = options.expires = new Date();
				t.setMilliseconds(t.getMilliseconds() + days * 864e+5);
			}

			return (document.cookie = [
				encode(key), '=', stringifyCookieValue(value),
				options.expires ? '; expires=' + options.expires.toUTCString() : '', // use expires attribute, max-age is not supported by IE
				options.path    ? '; path=' + options.path : '',
				options.domain  ? '; domain=' + options.domain : '',
				options.secure  ? '; secure' : ''
			].join(''));
		}

		// Read

		var result = key ? undefined : {},
			// To prevent the for loop in the first place assign an empty array
			// in case there are no cookies at all. Also prevents odd result when
			// calling $.cookie().
			cookies = document.cookie ? document.cookie.split('; ') : [],
			i = 0,
			l = cookies.length;

		for (; i < l; i++) {
			var parts = cookies[i].split('='),
				name = decode(parts.shift()),
				cookie = parts.join('=');

			if (key === name) {
				// If second argument (value) is a function it's a converter...
				result = read(cookie, value);
				break;
			}

			// Prevent storing a cookie that we couldn't decode.
			if (!key && (cookie = read(cookie)) !== undefined) {
				result[name] = cookie;
			}
		}

		return result;
	};

	config.defaults = {};

	$.removeCookie = function (key, options) {
		// Must not alter options, thus extending a fresh object...
		$.cookie(key, '', $.extend({}, options, { expires: -1 }));
		return !$.cookie(key);
	};

}));

/* Issue #924 is fixed for 5.3+ and 6.0. (compatibility with 5.3) */
if(window['PrimeFaces'] && window['PrimeFaces'].widget.Dialog) {
    PrimeFaces.widget.Dialog = PrimeFaces.widget.Dialog.extend({
        
        enableModality: function() {
            this._super();
            $(document.body).children(this.jqId + '_modal').addClass('ui-dialog-mask');
        },
        
        syncWindowResize: function() {}
    });
}
    

