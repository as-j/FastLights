definition(
    name: "Fast Motion and Mode Lights-2.0",
    namespace: "asj",
    author: "asj",
    parent: "asj:Fast Lights Main",
    description: "Simplified lighting activation that tries to be as fast as possible",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section("App Name") {
                label title: "App Name", defaultValue: app.label, required: true
            }
            section("Turn On Trigger") {
                input "turnOnMotionSensor", "capability.motionSensor", title: "Motion Sensor Becomes Active", multiple: true, required: false
                input "turnOnContactSensor", "capability.contactSensor", title: "Contacts Open", multiple: true, required: false
            }
            section("Turn Off Triggers") {
                input "timeOffS", "number", title: "Wait time to turn off (s)", defaultValue: 120, required: false
                input "turnOffMotionSensor", "capability.motionSensor", title: "Motion Sensor Becomes Inactive", multiple: true, required: false
                input "turnOffContactSensor", "capability.contactSensor", title: "Contacts Close", multiple: true, required: false
            }
            section("Defaults for all modes") {
                input "level_mode", "number", title: "Default Level", defaultValue: 50, require: false
                input "temp_default", "number", title: "Default Temp", require: false
                input "switch_default", "capability.switch", title: "Switches", multiple: true, required: false
            }
            section("Per Mode Overrides") {
                href(name: "href",
                     title: "Settings to change per mode",
                     required: false,
                     page: "perModeOverride")
            }
            section("Debug Settings") {
                //standard logging options
                input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
                input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
            }
        }
        page(name: "perModeOverride", title: "Settings to override per mode") {
            location.modes.each { mode ->
                section("Mode: $mode") {
                    input "level_$mode", "number", title: "Level: $mode", defaultValue: 50, require: false
                    input "temp_$mode", "number", title: "Color Temp: $mode", require: false
                    input "switch_$mode", "capability.switch", title: "Devices for this mode only", multiple: true, required: false
                }
            }
        }

    }
}


/**
 *  installed()
 *
 *  Runs when the app is first installed.
 **/
def installed() {
    state.installedAt = now()
    if (logEnable) log.debug "${app.label}: Installed with settings: ${settings}" 
    updated()
}

/**
 *  uninstalled()
 *
 *  Runs when the app is uninstalled.
 **/
def uninstalled() {
    unschedule()
    unsubscribe()
    if (logEnable) log.debug "${app.label}: Uninstalled"
}

/**
 *  updated()
 * 
 *  Runs when app settings are changed.
 * 
 *  Updates device.state with input values and other hard-coded values.
 *  Builds state.deviceAttributes which describes the attributes that will be monitored for each device collection 
 *  Refreshes scheduling and subscriptions.
 **/
def updated() {
    if (logEnable) log.debug "${app.label}: updated ${settings}"

    unsubscribe()
    unschedule()

    // Turn on devices
    settings.turnOnMotionSensor.each { device ->
        subscribe(device, "motion.active", turnOnEvent)
    }
    settings.turnOnContactSensor.each { device ->
        subscribe(device, "contact.open", turnOnEvent)
    }

    // Turn off devices
    settings.turnOffMotionSensor.each { device ->
        subscribe(device, "motion.inactive", turnOffEvent)
    }
    settings.turnOffContactSensor.each { device ->
        subscribe(device, "contact.closed", turnOffEvent)
    }
    settings.switch_default.each { device ->
        if (logEnable) log.debug "turnOnEvent(): ${device.currentValue("level")}"
    }
}

def turnOnEvent(evt) {
    def mode = location.currentMode
    state.turnOnMode = mode
    def mode_level = settings.level_default
    def mode_temp = settings.temp_default
    def devices = settings.switch_default

    if (logEnable) log.debug "turnOnEvent(): $evt.displayName($evt.name) $evt.value swtich_$mode: ${settings."switch_$mode"}"

    if (settings."switch_$mode") devices = settings."switch_$mode"
    if (settings."level_$mode") mode_level = settings."level_$mode"
    if (settings."temp_$mode") mode_temp = settings."temp_$mode"

    if (logEnable) log.debug "turnOnEvent(): devices: $devices"

    devices.each { device ->
        if (mode_level && device.hasAttribute("level")) {
            if (device.currentValue('level') != mode_level) device.setLevel(mode_level)
        }
        if (mode_temp && device.hasAttribute("colorTemperature")) {
            if (device.currentValue('colorTemperature') != mode_temp) device.setColorTemperature(mode_temp)
        }
        device.on()
    }
    def text ="${app.label} turn on event"
    if (txtEnable) log.info text
    sendEvent(name: "switch", value: "on", descriptionText: text)
}

def turnOffEvent(evt) {
    if (logEnable) log.debug "turnOffEvent(): $evt.displayName($evt.name) $evt.value"
    if (settings.timeOffS) {
        unschedule()
        def date = new Date()
        date.setTime(now() + settings.timeOffS*1000)
        schedule(date, "turnOff")
        def text ="${app.label} scheduled turn off event"
        if (txtEnable) log.info text
        sendEvent(name: "switch", value: "scheduled", descriptionText: text)
    } else {
        turnOff()
    }
}

def turnOff() {
    if (logEnable) log.debug "turnOff(): Turn on mode: ${state.turnOnMode}"

    def mode = state.turnOnMode
    def devices = settings.switch_default

    if (settings."switch_$mode") devices = settings."switch_$mode"

    devices.each { device ->
        device.off()
    }
    def text ="${app.label} turn off event"
    if (txtEnable) log.info text
    sendEvent(name: "switch", value: "off", descriptionText: text)
}
