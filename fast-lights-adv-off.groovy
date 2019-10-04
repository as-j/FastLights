definition(
    name: "Fast Motion and Mode with Adv Off",
    namespace: "asj",
    author: "asj",
    parent: "asj:Fast Lights Main",
    description: "Fast Motion and Mode with controllable Off time",
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
                input "timeOffS", "number", title: "Turn off after motion ends or contact closes (s)", defaultValue: 120, required: false
                input "timeForceOffS", "number", title: "Turn off even if motion active or contact open (s)"
                input "turnOffMotionSensor", "capability.motionSensor", title: "Motion Sensor Becomes Inactive", multiple: true, required: false
                input "turnOffContactSensor", "capability.contactSensor", title: "Contacts Close", multiple: true, required: false
            }
            section("Defaults for all modes") {
                input "level_default", "number", title: "Default Level", require: false
                input "temp_default", "number", title: "Default Temp", require: false
                input "switch_default", "capability.switch", title: "Devices To Turn On", multiple: true, required: false
                input "switch_off_default", "capability.switch", title: "Devices To Turn Off", multiple: true, required: false
            }
            section("Advanced Settings") {
                href(name: "href",
                     title: "Settings to change per mode",
                     required: false,
                     page: "perModeOverride")
                href(name: "href",
                     title: "Advanced Off Time Controls",
                     required: false,
                     page: "offTimeControls")
            }
            section("Debug Settings") {
                //standard logging options
                input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
                input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: false
            }
        }
        page(name: "perModeOverride", title: "Settings to override per mode") {
            section() {
                if (settings?.level_default) paragraph "Default Level: ${settings.level_default}"
                if (settings?.temp_default) paragraph "Default Color Temperature: ${settings.temp_default}"
                if (settings?.switch_default) paragraph "Default On Devices: ${settings.switch_default}"
                if (settings?.switch_off_default) paragraph "Default Off Devices: ${settings.switch_off_default}"
            }
            section() {
                input "override_modes", "mode", title: "Modes to override defaults", require: false, multiple: true, submitOnChange: true
            }
            settings?.override_modes.each { mode ->
                section("Mode: $mode", hideable: true) {
                    input "level_$mode", "number", title: "Level: $mode", require: false
                    input "temp_$mode", "number", title: "Color Temp: $mode", require: false
                    input "switch_$mode", "capability.switch", title: "Devices To Turn On for $mode", multiple: true, required: false
                    input "switch_off_$mode", "capability.switch", title: "Devices To Turn Off for $mode", multiple: true, required: false
                }
            }
        }
        page(name: "offTimeControls", title: "Advanced Off Time Controls") {
            section("Defaults") {
                paragraph "Default Off Time: ${settings?.timeOffS}"
                paragraph "Forced Off Time: ${settings?.timeOffForceS}"
            }
            section("Extend off times by") {
                input "extOffTimes", "bool", title: "Enable off time extention", defaultValue: false, submitOnChange: true
                if (settings?.extOffTimes) {
                    input "timeMaxOffS", "number", title: "Max Off Time (s)", require: false
                    input "timeResetIdleS", "number", title: "When Inactive For This Time Reset Off Time (s)", require: false
                    input "timeOnScaling", "decimal", title: "Speed of accumulation when Action", defaultValue: 2.0, require: false
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
 *  Refreshes scheduling and subscriptions.
 **/
def updated() {
    if (logEnable) log.debug "${app.label}: updated ${settings}"

    unsubscribe()
    // If lights are on don't unschedule and remove turn off schedules
    //unschedule()

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
    state.turnOnMode = mode.name
    def devices = settings.switch_default
    def devices_off = settings.switch_off_default
    def mode_level = settings.level_default
    def mode_temp = settings.temp_default

    if (logEnable) log.debug "turnOnEvent(): $evt.displayName($evt.name) $evt.value swtich_$mode: ${settings."switch_$mode"} switch_off_$mode: ${settings."switch_off_$mode"}"

    if ((settings."switch_$mode") || (settings."switch_off_$mode")) {
        devices = settings."switch_$mode"
        devices_off = settings."switch_off_$mode"
    }
    if (settings."level_$mode") mode_level = settings."level_$mode"
    if (settings."temp_$mode") mode_temp = settings."temp_$mode"

    if (logEnable) log.debug "turnOnEvent(): devices: $devices/$mode_level/$mode_temp"

    devices.each { device ->
        if (logEnable) log.debug "turnOnEvent(): turning on $device"
        if (mode_level && device.hasCommand("setLevel")) {
            if (logEnable) log.debug "turnOnEvent(): has setLevel ${device.currentValue('level')}"
            if (device.currentValue('level') != mode_level) device.setLevel(mode_level)
        }
        if (mode_temp && device.hasCommand("setColorTemperature")) {
            if (device.currentValue('colorTemperature') != mode_temp) device.setColorTemperature(mode_temp)
        }
        if (device.currentValue('switch') != "on") device.on()
    }

    devices_off.each { device ->
        if (logEnable) log.debug "turnOnEvent(): turning off $device"
        if (device.currentValue('switch') != "off") device.off()
    }

    def text ="${app.label} turn on event"
    if (txtEnable) log.info text
    sendEvent(name: "switch", value: "on", descriptionText: text)

    unschedule()

    if (settings.timeForceOffS) {
        def date = new Date()
        date.setTime(now() + settings.timeForceOffS*1000)
        schedule(date, "turnOff")
    }

    advTimeOnCalc()
}

def turnOffEvent(evt) {
    if (logEnable) log.debug "turnOffEvent(): $evt.displayName($evt.name) $evt.value"
    long timeOffS = advTimeOffCalc(settings?.timeOffS)
    if (timeOffS) {
        unschedule()
        def date = new Date()
        date.setTime(now() + timeOffS*1000)
        schedule(date, "turnOff")
        date.setTime(now() + (timeOffS + settings.timeResetIdleS)*1000)
        schedule(date, "advReset")
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

    if (logEnable) log.debug "turnOff(): Default: $device Per Mode switch_$mode ${settings."switch_$mode"}"

    if (settings."switch_$mode") devices = settings."switch_$mode"

    devices.each { device ->
        if (logEnable) log.debug "turnOff(): turning off $device"
        device.off()
    }
    def text ="${app.label} turn off event"
    if (txtEnable) log.info text
    sendEvent(name: "switch", value: "off", descriptionText: text)
}

def advTimeOnCalc() {
    if (logEnable) log.debug "advTimeOnCalc(): advTimeOff: ${state?.advTimeOffS}"
    if (!settings?.extOffTimes) return

    state.turnOnAt = now()
    if (!state.turnOffAt) return

    long delta = (state.turnOnAt - state.turnOffAt)/1000
    if (delta < 0) delta = 0

    if(!state?.advTimeOffS) state.advTimeOffS = settings.timeOffS

    state.advTimeOffS -= delta
    if (state.advTimeOffS < settings.timeOffS) {
        state.advTimeOffS = settings.timeOffS
    }

    if (logEnable) log.debug "advTimeOnCalc(): new advTimeOff: ${state?.advTimeOffS} delta: ${delta}"
}

def advTimeOffCalc(timeOffS) {
    if (logEnable) log.debug "advTimeOffCalc(): advTimeOff: ${state.advTimeOffS}"
    if (!settings?.extOffTimes) return timeOffS
    if (!state?.turnOnAt) return timeOffS

    state.turnOffAt = now()

    long delta = (state.turnOffAt - state.turnOnAt)/1000
    if (delta < 0) delta = 0

    if (!state?.advTimeOffS) state.advTimeOffS = settings.timeOffS

    state.advTimeOffS += delta * settings.timeOnScaling 
    if (state.advTimeOffS > settings.timeMaxOffS) state.advTimeOffS = settings.timeMaxOffS

    if (logEnable) log.debug "advTimeOffCalc(): new advTimeOff: ${state.advTimeOffS} delta: ${delta}"
    return state.advTimeOffS

}

def advReset() {
    state.advTimeOffS = settings.timeOffS
}
