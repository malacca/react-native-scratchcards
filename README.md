
原始代码来自于 [react-native-scratch](https://github.com/ConduitMobileRND/react-native-scratch)，由于进行了魔改，有 breaking change，所以就没有 fork 原代码


# react-native-scratchcards

## install

`yarn add react-native-scratchcards`


#### Android

6.0 以上啥都不用干，低版本没测，应该是 `react-native link` 就可以了

#### iOS

暂未完成



## Usage

```js
import React, { Component } from 'react';
import { View } from 'react-native';
import ScratchView from 'react-native-scratchcards'

class MyView extends Component {

	render() {
	  return (<View style={{ width: 300, height: 300 }}>

		<ComponentA> // 刮刮卡覆盖的组件，刮开后显示的就是他了

		<ScratchView
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

			// 开始载入 (此时可能还没载入完成)
			onInit={Callback}

			// 若设置了背景图, 背景图加载 成功/失败 回调
			onImageLoad={Callback}
			onImageError={Callback}

			// 刮开触摸 开始/结束 回调
			onTouchStart={Callback}
			onTouchEnd={Callback}
			
			// 当前刮开比例 (该值并不及时, 仅做参考)
			onProgress={Callback}
			
			// 当刮开比例达到 threshold 设定值时回调
			onDone={Callback}
		/>
	  </View>)
}
export default MyView;
```

额外说明：

1. 考虑到计算性能, 组件尺寸 和 围栏设置(fence) 只处理一次, 后续修改无效。
	若必须修改，重新创建组件
2. 其他属性可进行更新	
