// declare app object
function Wscan() {
    // options
    var barcodetimeout = 2; // 2 second barcode timeout
    var defaultCallback = function(barcode){ alert(barcode); };
    var fireOnceCallback = null;
    // Public members
    this.startScanning = function () {
        startScanning(true);
    };
    this.stopScanning = function () {
        stopScanning();
    };
    this.scanOnce = function (callback) {
        if (callback != null) {
            fireOnceCallback = function (barcode) {
                callback(barcode);
                fireOnceCallback = null;
            }
        }
        startScanning(false);
    };
    this.setFireOnceCallback = function (callback) {
        fireOnceCallback = function (barcode) {
            callback(barcode);
            fireOnceCallback = null;
        };
    };
    this.setDefaultCallback = function(callback){
        defaultCallback = callback;
    };
    this.loadData = function () {
        loadData();
    };
    this.clearLastCode = function () {
        setBarcodeStat("No barcode detected");
        lastbarcode = 0;
    };
    this.turnOffCamera = function(){
        turnOffCamera();
    };
    // contructor code & init function; set params
    this.init = function(callback, chkconnection){
        if (callback != null) {
            defaultCallback = callback;
        }
        // add orientation event listener
        window.addEventListener('orientationchange', rotateImage);
        // check if barcode server available
        if (chkconnection)
            if (!isAppOnline()){
                openApp();
            }
        // set unload listener
        $(window).unload(function(){
            getSyncData("stopserver");
        });
    };
    // Core functions for loading data and disabling load timers
    var timer = false;
    var continuous = false;
    var errorCount = 0;
    function isAppOnline(){
        var response = getSyncData("");
        return response.status == 200;
    }
    function openApp(){
        if( !(/Android/i.test(navigator.userAgent)) ) {
            alert("Barcode scanning requires an Android device");
            return;
        }
        var answer = confirm("An android app is require to scan barcodes,\nwould you like to open or install?");
        if (answer)
            document.location.href = "https://play.google.com/store/apps/details?id=com.google.wscan.client.android";
    }
    function turnOffCamera(){
        if (timer==false){ // check if the camera is being used again
            getSyncData('stopcamera');
        }
    }
    function getSyncData(request){
        try {
            return $.ajax({
                url: 'http://127.0.0.1:8081/'+request,
                type: 'GET',
                cache: false,
                dataType: 'json',
                async: false
            });
        } catch (ex){
            return false;
        }
    }
    function loadData() {
        $.ajax({
            url: 'http://127.0.0.1:8081/getdata',
            type: 'GET',
            cache: false,
            dataType: 'json',
            success: function (data, textStatus, jqXHR) {
                setPreviewImage(data.prevdata);
                processBarcode(data.barcode);
                return true;
            },
            error: function (jqXHR, textStatus, errorThrown) {
                processError();
            }
        });
    }
    function processError(){
        if (errorCount > 0) {
            stopScanning();
            errorCount = 0;
            if (!isAppOnline()){
                openApp();
            }
        } else {
            processBarcode("");
        }
        errorCount++;
    }

    function setLoadDataTimeout() {
        setTimeout('$.wscan.loadData();', 120);
    }

    function startTimer() {
        timer = true;
        loadData();
    }

    function stopTimer() {
        timer = false;
        continuous = false;
    }

    function stopScanning() {
        closeUI();
        stopTimer();
        setTimeout('$.wscan.turnOffCamera();', 5000);
    }

    function startScanning(cont) {
        continuous = cont;
        openUI();
        startTimer();
    }

    var lastbarcode;
    var timeoutId = 0;

    function processBarcode(barcode) {
        //console.log(barcode);
        if (barcode != "" && barcode != lastbarcode) {
            setBarcodeStat("Barcode: " + barcode);
            lastbarcode = barcode;
            // set barcode timeout
            clearTimeout(timeoutId);
            timeoutId = setTimeout('$.wscan.clearLastCode();', barcodetimeout * 1000);
            // continuous scan? Get more data, otherwise quit
            if (timer && continuous) {
                setLoadDataTimeout();
            } else {
                stopScanning();
            }
            // trigger callback
            if (fireOnceCallback != null) {
                fireOnceCallback(barcode);
            } else {
                defaultCallback(barcode);
            }
        } else {
            if (timer) {
                setLoadDataTimeout();
            }
        }
    }

    // UI functions
    var uiloaded = false;

    function openUI() {
        if (!uiloaded) {
            // append html
            $('body').append('<div id="wscan-contain" class="header-color-blue" style="display: none; position: absolute; width: 170px; left:50%; top:45px; margin-left:-85px; text-align: center; border: 1px solid #FFFFFF; border-radius: 4px; z-index: 1024;">' +
                '<button onclick="$.wscan.stopScanning();" class="header-color-red" style="border: 0px solid; border-radius: 50%; position: absolute; right: 0px; width: 20px; height: 20px; color: white; margin-top: -5px; margin-right: -5px; font-family: sans-serif;">X</button>' +
                '<span style="font-family: sans-serif;">Scan barcode</span>' +
                '<div style="height: 150px; width: 150px; overflow: hidden; text-align: center; margin: 10px; margin-top: 0px; margin-bottom: 0px; background-color: #000000;">' +
                '<img style="vertical-align: middle; width: 100%; height: 100%;" id="wscan-preview" src=""/><br/>' +
                '<img style="margin-top:50px;" id="wscan-loader" src="data:image/gif;base64,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"/>' +
                '</div>' +
                '<span id="wscan-barcode" style="font-family: sans-serif; font-size: 12px;">No barcode detected</span><br/>' +
                '</div>');
            uiloaded = true;
        }
        rotateImage();
        toggleLoader(true);
        $("#wscan-contain").fadeIn(500);
    }

    function toggleLoader(show){
        if (show){
            $("#wscan-preview").hide();
            $("#wscan-loader").show();
        } else {
            $("#wscan-preview").show();
            $("#wscan-loader").hide();
        }
    }

    function closeUI() {
        $("#wscan-contain").fadeOut(500);
    }

    function setPreviewImage(img) {
        if (img!=""){
            $("#wscan-preview").attr("src", "data:image/jpeg;base64," + img);
            toggleLoader(false);
        }
    }

    function setBarcodeStat(barcode) {
        $("#wscan-barcode").text(barcode);
    }

    function rotateImage() {
        if (uiloaded)
            switch (window.orientation) {
                case -90:
                case 90:
                    $("#wscan-preview").removeClass("rotate");
                    break;
                default:
                    $("#wscan-preview").addClass("rotate");
                    break;
            }
    }
    return this;
}
// init jquery extention
(function ($) {
    $.extend({
        wscan: new Wscan()
    });
})(jQuery);