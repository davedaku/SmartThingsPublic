/**
 *  Temporarily Brighten Lights On Motion
 *
 *  Copyright 2017 David Daku
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: 'Temporarily Brighten Lights On Motion',
    namespace: 'davedaku',
    author: 'David Daku',
    description: 'Brightens lights when motion is detected, then dims them back to their prior level after motion has stopped.',
    category: 'My Apps',
    iconUrl: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png',
    iconX2Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png',
    iconX3Url: 'https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png')


preferences {
	section('When motion is detected by:') {
		input 'theMotion', 'capability.motionSensor', required: true, title: 'Which sensor?'
	}
    section('Brighten:') {
        input 'theDimmers', 'capability.switchLevel', required: true, multiple: true, title: 'Which dimmable switches?'
        input 'brightLevel', 'number', required: true, defaultValue: 100, range: 0..100, title: 'Brighten to what level?'
        input 'brightenRate', 'number', required: true, defaultValue: 100, range: 0..9, title: 'At what rate?'
    }
    section('Brighten even if off?') {
    	input 'evenIfOff', 'boolean', defaultValue: false
    }
    section('Dim after inactive for:') {
    	input 'inactivityLength', 'number', required: true, defaultValue: 3, range: 0..999, title: 'How many minutes?'
    }    
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
	state.inactivityThreshold = 1000 * 60 * inactivityLength // inactivityLength is in Minutes   
    
    subscribe(theMotion, 'motion.active', motionActionHandler)
    subscribe(theMotion, 'motion.inactive', motionInactionHandler)
}

def motionActionHandler(evt) {
    log.debug "motionActionHandler called to set lights to ${settings.brightLevel}"
    
    if (state.brightened == true) {
    	log.debug '  lights are already brightened'
    } else {
		def levels = [:]
    
    	for (def i = 0; i < settings.theDimmers.size(); i++) {
        	log.debug "  looking at dimmer #${i}"
    		def light = settings.theDimmers[i]
            def level = light.level.toInteger()
            log.debug "  ...is at ${level}"
            
            if (light != null && ((settings.evenIfOff == true || level != 0) && level < settings.brightLevel)) {
    			levels.put(i, settings.theDimmers[i].level)
                light.setLevel(settings.brightLevel, settings.brightenRate)
                state.brightened = true
            }
        }
        
    	state.previousLevels = levels;
    }
}

def motionInactionHandler(evt) {
	//if (state.brightened) {
    	runIn(60 * inactivityLength, dimIfInactive)
    //}
}

def dimIfInactive() {
	log.debug 'dimIfInactive called'
    
    def motionState = settings.theMotion.currentState('motion')
    
    if (motionState.value == 'inactive') {
    	def elapsedSinceActive = now() - motionState.date.time
        
        if (elapsed >= state.threshold) {
        	log.debug "  will dim the lights back to their previous level"
            dimBackToPrevious()
        } else {
        	log.debug "  motion was active ${elapsed / 1000} sec ago (within threshold)"
            // todo: reschedule?
        }
    }    
}

def dimBackToPrevious() {
    for (i = 0; i < settings.theDimmers.size(); i++) {
    	def previousLevel = state.previousLevels[i]
        def light = settings.theDimmers[i]
        
        if (previousLevel != null && light != null) {            
            if (light.level > previousLevel) {
            	light.setLevel(previousLevel, settings.brightenRate)
            }            
        }
    }
    
    state.brightened = false
}