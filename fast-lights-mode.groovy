definition(
    name: "Fast Motion and Mode Lights",
    namespace: "asj",
    author: "asj",
    description: "Simplified lighting activation that tries to be as fast as possible",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png") {

    preferences {
        page(name: "mainPage", title: "Settings Page", install: true, uninstall: true) {
            section("Turn On Trigger") {
                input "turnOnMotionSensor", "capability.motionSensor", title: "Motion Sensor Becomes Active", multiple: true, required: false
                input "turnOnContactSensor", "capability.contactSensor", title: "Contacts Open", multiple: true, required: false
            }
            section("Turn Off Triggers") {
                input "timeOffS", "number", title: "Wait time to turn off (s)", defaultValue: 120, required: false
                input "turnOffMotionSensor", "capability.motionSensor", title: "Motion Sensor Becomes Inactive", multiple: true, required: false
                input "turnOffContactSensor", "capability.contactSensor", title: "Contacts Close", multiple: true, required: false
            }
            section("Devices For All Modes (unless specified otherwise)") {
                input "deviceSwitch", "capability.switch", title: "Switches", multiple: true, required: false
            }
            location.modes.each { mode ->
                section("Mode: $mode") {
                    input "level_$mode", "number", title: "Level: $mode", defaultValue: 50, require: false
                    input "temp_$mode", "number", title: "Color Temp: $mode", require: false
                    input "switch_$mode", "capability.switch", title: "Devices for this mode only", multiple: true, required: false
                }
            }
            section("Debug Settings") {
                //standard logging options
                input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
                input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
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
}

def turnOnEvent(evt) {
    if (logEnable) log.debug "turnOnEvent(): $evt.displayName($evt.name) $evt.value"

    def mode = location.currentMode
    def mode_level = settings."level_$mode"
    def mode_temp = settings."temp_$mode"

    def devices = settings.deviceSwitchLevel

    if (!settings."switch_$mode".isEmpty()) devices = settings."switch_$mode"

    devices.each { device ->
        if (mode_level && device.hasAttribute("level")) {
            if (device.level != mode_level) device.setLevel(mode_level)
        }
        if (mode_temp && device.hasAttribute("colorTemperature")) {
            if (device.colorTemperature != mode_temp) device.setColorTemperature(mode_temp)
        }
        device.on()
    }
}

def turnOffEvent(evt) {
    if (logEnable) log.debug "turnOffEvent(): $evt.displayName($evt.name) $evt.value"
    if (settings.timeOffS) {
        unschedule()
        def date = new Date()
        date.setTime(now() + settings.timeOffS*1000)
        schedule(date, "turnOff")
    } else {
        turnOff()
    }
}

def turnOff() {
    if (logEnable) log.debug "turnOff(): no args"
    settings.deviceSwitchLevel.each { device ->
        device.off()
    }

    settings.deviceSwitch.each { device ->
        device.off()
    }

    settings.deviceBulb.each { device ->
        device.off()
    }
}
