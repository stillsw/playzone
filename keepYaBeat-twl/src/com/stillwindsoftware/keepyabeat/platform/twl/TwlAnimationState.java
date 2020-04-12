package com.stillwindsoftware.keepyabeat.platform.twl;

import com.stillwindsoftware.keepyabeat.platform.PlatformAnimationState;

import de.matthiasmann.twl.AnimationState;

public class TwlAnimationState extends AnimationState implements PlatformAnimationState {
	
	@Override
	public void setState(PlatformAnimationStateKey stateKey, boolean on) {
		this.setAnimationState(((TwlAnimationStateKey)stateKey).getStateKey(), on);
	}

	public static class TwlAnimationStateKey implements PlatformAnimationStateKey {
		
		private StateKey stateKey;
		
		public TwlAnimationStateKey(StateKey stateKey) {
			this.stateKey = stateKey;
		}

		public StateKey getStateKey() {
			return stateKey;
		}
		
	}

}

