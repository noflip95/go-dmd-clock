package com.rinke.solutions.pinball.view.handler;

import lombok.extern.slf4j.Slf4j;

import com.rinke.solutions.beans.Autowired;
import com.rinke.solutions.beans.Bean;
import com.rinke.solutions.beans.Value;
import com.rinke.solutions.pinball.animation.Animation;
import com.rinke.solutions.pinball.animation.Animation.EditMode;
import com.rinke.solutions.pinball.animation.CompiledAnimation;
import com.rinke.solutions.pinball.model.PalMapping.SwitchMode;
import com.rinke.solutions.pinball.util.ObservableMap;
import com.rinke.solutions.pinball.view.model.ViewModel;

@Bean
@Slf4j
public class CutCmdHandler extends AbstractCommandHandler implements ViewBindingHandler {

	@Value(defaultValue="4") 
	int noOfPlanesWhenCutting;
	
	@Value boolean addPalWhenCut;
	@Value boolean createBookmarkAfterCut;
	@Value boolean autoKeyframeWhenCut;
	
	@Autowired PaletteHandler paletteHandler;
	@Autowired BookmarkHandler bookmarkHandler;
	@Autowired KeyframeHandler keyframeHandler;
	
	public CutCmdHandler(ViewModel vm) {
		super(vm);
	}
	
	public void onSelectedRecordingChanged(Animation o, Animation n) {
		vm.setMarkStartEnabled(n!=null);
	}

	public void onSelectedSceneChanged(Animation o, Animation n) {
		vm.setMarkStartEnabled(n!=null);
	}
	
	public void onSelectedFrameChanged(int o, int n) {
		vm.setSceneCutEnabled(vm.cutInfo.canCut());
		vm.setMarkEndEnabled(vm.cutInfo.canMarkEnd(n));
	}

	Animation getSourceAnimation() {
		if( vm.selectedRecording != null ) return vm.selectedRecording;
		else if ( vm.selectedScene != null ) return vm.selectedScene;
		return null;
	}

	public void onMarkStart() {
		if( getSourceAnimation() != null ) {
			vm.cutInfo.setStart(getSourceAnimation().actFrame);
			vm.setSceneCutEnabled(vm.cutInfo.canCut());
			vm.setMarkEndEnabled(vm.cutInfo.canMarkEnd(getSourceAnimation().actFrame));
		}
	}

	public void onMarkEnd() {
		if( getSourceAnimation() != null ) {
			vm.cutInfo.setEnd(getSourceAnimation().actFrame);
			vm.setSceneCutEnabled(vm.cutInfo.canCut());
		}
	}

	public void onCutScene() {
			// respect number of planes while cutting / copying
		if( getSourceAnimation() != null ) {
			cutScene(getSourceAnimation(), vm.cutInfo.getStart(), vm.cutInfo.getEnd(), buildUniqueName(vm.scenes));
			log.info("cutting out scene from {}", vm.cutInfo);
			vm.cutInfo.reset();
		}
		
	}
	
	/**
	 * creates a unique key name for scenes
	 * @param anis the map containing the keys
	 * @return the new unique name
	 */
	public <T extends Animation> String buildUniqueName(ObservableMap<String, T> anis) {
		int no = anis.size();
		String name = "Scene " + no;
		while( anis.containsKey(name)) {
			no++;
			name = "Scene " + no;
		}
		return name;
	}
	
	public Animation cutScene(Animation animation, int start, int end, String name) {
		CompiledAnimation cutScene = animation.cutScene(start, end, noOfPlanesWhenCutting);
		
		if( addPalWhenCut )
			paletteHandler.copyPalettePlaneUpgrade();
		
		cutScene.setDesc(name);
		cutScene.setPalIndex(vm.selectedPalette.index);
		cutScene.setProjectAnimation(true);
		cutScene.setEditMode(EditMode.COLMASK);
				
		vm.scenes.put(name, cutScene);
		
		if( createBookmarkAfterCut )
			bookmarkHandler.addBookmark(animation, name, start);
		
		vm.setSelectedFrameSeq(cutScene);

		if( autoKeyframeWhenCut ) {
			if( vm.selectedRecording!=null ) keyframeHandler.onAddKeyFrame(SwitchMode.REPLACE);
		}

		vm.setSelectedScene(cutScene);

		return cutScene;
	}

}