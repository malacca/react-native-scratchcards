import React, { Component } from 'react';
import {StyleSheet, Animated, requireNativeComponent, Image} from 'react-native';
const RNTScratchCards = requireNativeComponent('RNTScratchCards', ScratchView);
const ScratchAnimated = RNTScratchCards && Animated.createAnimatedComponent(RNTScratchCards);

class ScratchView extends Component {
    touchStart = false;
    opacity = new Animated.Value(1);
    state = {
        visible: true,
    };

    fadeOut = (callback) => {
        if (!this.state.visible) {
            return;
        }
        Animated.timing(this.opacity, {
            toValue: 0,
            duration: 300,
            useNativeDriver: true,
        }).start(callback);
    }

    _onScratchEvent = (e) => {
        const props = this.props;
        const {event, extra} = e.nativeEvent;
        switch (event) {
            case "onInit":
                props.onInit && props.onInit();
                break;
            case "onProgress":
                props.onProgress && props.onProgress(extra);
                break;
            case "onTouchStart":
                if (!this.touchStart) {
                    this.touchStart = true;
                    props.onTouchStart && props.onTouchStart();
                }
                break;
            case "onTouchEnd":
                if (this.touchStart) {
                    this.touchStart = false;
                    props.onTouchEnd && props.onTouchEnd();
                }
                break;
            case "onDone":
                if (props.fadeOut === false) {
                    props.onDone && props.onDone();
                } else {
                    this.fadeOut(() => {
                        this.setState({visible: false})
                        // set visible 会移除组件, 会触发 onTouchEnd
                        // 但此时组件已不在, 无法触发回调, 保证 touch 事件成对出现在某些情况下可能很有用
                        // 所以这里手动触发一个
                        if (this.touchStart) {
                            this.touchStart = false;
                            props.onTouchEnd && props.onTouchEnd();
                        }
                        props.onDone && props.onDone();
                    })
                }
                break;
            case "onImageLoad":
                props.onImageLoad && props.onImageLoad();
                break;
            case "onImageError":
                props.onImageError && props.onImageError();
                break;
            default:
                break;
        }
    }

    render() {
        if (ScratchAnimated && this.state.visible) {
            const {
                source, 
                onInit, onProgress, onDone,
                onImageLoad, onImageError,
                onTouchStart, onTouchEnd,
                ...props
            } = this.props;
            props.source = source ? Image.resolveAssetSource(source) : {uri: null};
            props.listeners = {
                onInit: Boolean(onInit),
                onProgress: Boolean(onProgress),
                onImageLoad: Boolean(onImageLoad),
                onImageError: Boolean(onImageError),
                onTouchStart: Boolean(onTouchStart),
                onTouchEnd: Boolean(onTouchEnd)
            };
            return (
                <ScratchAnimated
                    {...props}
                    style={[StyleSheet.absoluteFill, { opacity: this.opacity }]}
                    onScratchEvent={this._onScratchEvent}
                />
            );
        }
        return null;
    }
}

export default ScratchView
