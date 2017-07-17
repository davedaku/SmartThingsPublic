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
    unschedule()
	initialize()
}

def initialize() {
	state.inactivityThreshold = 1000 * 60 * inactivityLength // inactivityLength is in Minutes   
    
    if (theDimmers != null && theDimmers != "") {
        subscribe(theMotion, 'motion.active', motionActionHandler)
        subscribe(theMotion, 'motion.inactive', motionInactionHandler)
    }
}

def motionActionHandler(evt) {
    log.debug "motionActionHandler called (set lights to ${settings.brightLevel})"
    
    if (state.brightened != true) {
    	brightenAllLights()
    }
}

def motionInactionHandler(evt) {
    log.debug "scheduling dimIfInactive() in ${inactivityLength} minutes (${60 * inactivityLength} seconds)"
    
    runIn(60 * inactivityLength, dimIfInactive)
}

def brightenAllLights() {
	log.debug "brightenAllLights()  evenIfOff=${evenIfOff}"

	def originalLevels = new int[theDimmers?.size()]
    def i = 0
    for (dimmer in theDimmers) {
    	def currLvl = dimmer.currentLevel
        if (currLvl == null) {
        	currLvl = 0
        }
        
        originalLevels[i] = currLvl
        log.debug "  ${i}	current=${currLvl} to=${brightLevel}"

		if (currLvl != 0 || evenIfOff == "true") {
            if (currLvl < brightLevel) {
                log.debug "setting level of light #${i} to ${brightLevel}"
                dimmer.setLevel(brightLevel)
            }
        }
        
        i++
    }
    
    state.originalLevels = originalLevels
    state.brightened = true
}

def dimIfInactive() {
    def motionState = settings.theMotion.currentState('motion')
    
    if (motionState.value == 'inactive') {
    	def elapsedSinceActive = now() - motionState.date.time
        
        if (elapsedSinceActive >= state.inactivityThreshold) {
            lightsToOriginalState()
        }
    }    
}

def lightsToOriginalState() {
	log.debug "lightsToOriginalState()"

	def originalLevels = state.originalLevels
    def i = 0
    for (dimmer in theDimmers) {
    	if (i < originalLevels.size()) {
    		dimmer.setLevel(originalLevels[i])
        }
        i++
    }
    
    state.brightened = false
}
