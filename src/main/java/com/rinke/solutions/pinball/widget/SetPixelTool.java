package com.rinke.solutions.pinball.widget;

public class SetPixelTool extends DrawTool {

	public SetPixelTool(int actualColor) {
		super(actualColor);
	}

	@Override
	public boolean mouseMove(int x, int y) {
		if( pressedButton > 0 ) {
			dmd.setPixel(x, y, actualColor);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDown(int x, int y) {
		dmd.addUndoBuffer();
		dmd.setPixel(x, y, actualColor);
		return true;
	}
}
