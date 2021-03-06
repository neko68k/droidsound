package com.ssb.droidsound;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

public class FontTextView extends TextView {

	public FontTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
		String font = null;
		
		for(int i=0; i<attrs.getAttributeCount(); i++) {
			String name = attrs.getAttributeName(i);
			if(name != null && name.equals("font")) {
				font = attrs.getAttributeValue(i);
			}
		}
		
		if(font != null) {
			if(!isInEditMode()) {
				Typeface tf = Typeface.createFromAsset(context.getAssets(), "fonts/" + font + ".ttf");
				if(tf != null) {
					setTypeface(tf);
				}
			}
		}
	}

}
