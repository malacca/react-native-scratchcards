
原始代码来自于 [react-native-scratch](https://github.com/ConduitMobileRND/react-native-scratch)，由于进行了魔改，有 breaking change，所以就没有 fork 原代码


# react-native-scratchcard

## install

`yarn add react-native-scratchcard`


#### Android

6.0 以上啥都不用干，低版本没测，应该是 `react-native link` 就可以了

#### iOS

暂未完成



## Usage

```javascript
import React, { Component } from 'react';
import { View } from 'react-native';
import ScratchView from 'react-native-scratch'

class MyView extends Component {

	onImageLoadFinished = ({ id, success }) => {
	}
	onScratchProgressChanged = ({ value, id }) => {
	}
	onScratchDone = ({ isScratchDone, id }) => {
	}
	onScratchTouchStateChanged = ({ id, touchState }) => {
	}

	render() {
		return (<View style={{ width: 300, height: 300 }}>
			<ComponentA> // will be covered by the ScratchView
			<ComponentB> // will be covered by the ScratchView
			<ScratchView

				// 卡片ID(可选)
				id="string"

				// 笔触宽度
				brushSize={10} 

				// 围栏 [left, top, width, height], 不设置则为整个组件区域
				fence={[0, 0, 100, 200]}

				// 刮开百分比, 达到这个百分比认为有效刮开
				// 若设置了围栏, 这里的比例是针对围栏区域的, 在围栏区域以外, 不计入
				threshold={60} 

				// 达到有效刮开比例后, 是否隐藏组件, 默认为true
				fadeOut={false} 

				// 卡片背景色
				background="#AAAAAA" 

				// 卡片背景图, 设定值与 react image 的 source 一样
				// 可使用 远程/本地file/drawable/base64
				source={{uri: 'http://'}}

			    // 图片 mode 与 react image 组件类似
				resizeMode="contain|cover|stretch|center|repeat" 

				// 图片加载成功的回调
				onImageLoadFinished={this.onImageLoadFinished}

				// touch start/end 的回调
				onTouchStateChanged={this.onTouchStateChangedMethod}

				// 刮开比例发生变化时 的 回调
				onScratchProgressChanged={this.onScratchProgressChanged}

				// 达到有效刮开比例时 的 回调
				onScratchDone={this.onScratchDone}
			/>}
		</View>)
}
export default MyView;
```
