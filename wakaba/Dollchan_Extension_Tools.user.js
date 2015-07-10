// ==UserScript==
// @name            Dollchan Extension Tools
// @version         15.7.2.0
// @namespace       http://www.freedollchan.org/scripts/*
// @author          Sthephan Shinkufag @ FreeDollChan
// @copyright       (c) 2015 Dollchan Extension Tools Team. See the LICENSE file for license rights and limitations (MIT).
// @description     Doing some profit for imageboards
// @icon            https://raw.github.com/SthephanShinkufag/Dollchan-Extension-Tools/master/Icon.png
// @updateURL       https://raw.github.com/SthephanShinkufag/Dollchan-Extension-Tools/master/Dollchan_Extension_Tools.meta.js
// @run-at          document-start
// @grant           GM_getValue
// @grant           GM_setValue
// @grant           GM_deleteValue
// @grant           GM_openInTab
// @grant           GM_xmlhttpRequest
// @grant           unsafeWindow
// @include         *
// ==/UserScript==
(function de_main_func_outer() {

!function(global, framework, undefined){
'use strict';



 
var OBJECT          = 'Object'
  , FUNCTION        = 'Function'
  , ARRAY           = 'Array'
  , STRING          = 'String'
  , NUMBER          = 'Number'
  , REGEXP          = 'RegExp'
  , DATE            = 'Date'
  , MAP             = 'Map'
  , SET             = 'Set'
  , WEAKMAP         = 'WeakMap'
  , WEAKSET         = 'WeakSet'
  , SYMBOL          = 'Symbol'
  , PROMISE         = 'Promise'
  , MATH            = 'Math'
  , ARGUMENTS       = 'Arguments'
  , PROTOTYPE       = 'prototype'
  , CONSTRUCTOR     = 'constructor'
  , TO_STRING       = 'toString'
  , TO_STRING_TAG   = TO_STRING + 'Tag'
  , TO_LOCALE       = 'toLocaleString'
  , HAS_OWN         = 'hasOwnProperty'
  , FOR_EACH        = 'forEach'
  , ITERATOR        = 'iterator'
  , FF_ITERATOR     = '@@' + ITERATOR
  , PROCESS         = 'process'
  , CREATE_ELEMENT  = 'createElement'
 
  , Function        = global[FUNCTION]
  , Object          = global[OBJECT]
  , Array           = global[ARRAY]
  , String          = global[STRING]
  , Number          = global[NUMBER]
  , RegExp          = global[REGEXP]
  , Date            = global[DATE]
  , Map             = global[MAP]
  , Set             = global[SET]
  , WeakMap         = global[WEAKMAP]
  , WeakSet         = global[WEAKSET]
  , Symbol          = global[SYMBOL]
  , Math            = global[MATH]
  , TypeError       = global.TypeError
  , RangeError      = global.RangeError
  , setTimeout      = global.setTimeout
  , setImmediate    = global.setImmediate
  , clearImmediate  = global.clearImmediate
  , parseInt        = global.parseInt
  , isFinite        = global.isFinite
  , process         = global[PROCESS]
  , nextTick        = process && process.nextTick
  , document        = global.document
  , html            = document && document.documentElement
  , navigator       = global.navigator
  , define          = global.define
  , ArrayProto      = Array[PROTOTYPE]
  , ObjectProto     = Object[PROTOTYPE]
  , FunctionProto   = Function[PROTOTYPE]
  , Infinity        = 1 / 0
  , DOT             = '.'
 
  , CONSOLE_METHODS = 'assert,clear,count,debug,dir,dirxml,error,exception,' +
      'group,groupCollapsed,groupEnd,info,isIndependentlyComposed,log,' +
      'markTimeline,profile,profileEnd,table,time,timeEnd,timeline,' +
      'timelineEnd,timeStamp,trace,warn';


function isObject(it){
  return it !== null && (typeof it == 'object' || typeof it == 'function');
}
function isFunction(it){
  return typeof it == 'function';
}

var isNative = ctx(/./.test, /\[native code\]\s*\}\s*$/, 1);



var toString = ObjectProto[TO_STRING];
function setToStringTag(it, tag, stat){
  if(it && !has(it = stat ? it : it[PROTOTYPE], SYMBOL_TAG))hidden(it, SYMBOL_TAG, tag);
}
function cof(it){
  return toString.call(it).slice(8, -1);
}
function classof(it){
  var O, T;
  return it == undefined ? it === undefined ? 'Undefined' : 'Null'
    : typeof (T = (O = Object(it))[SYMBOL_TAG]) == 'string' ? T : cof(O);
}


var call  = FunctionProto.call
  , apply = FunctionProto.apply
  , REFERENCE_GET;

function part(){
  var fn     = assertFunction(this)
    , length = arguments.length
    , args   = Array(length)
    , i      = 0
    , _      = path._
    , holder = false;
  while(length > i)if((args[i] = arguments[i++]) === _)holder = true;
  return function(){
    var that    = this
      , _length = arguments.length
      , i = 0, j = 0, _args;
    if(!holder && !_length)return invoke(fn, args, that);
    _args = args.slice();
    if(holder)for(;length > i; i++)if(_args[i] === _)_args[i] = arguments[j++];
    while(_length > j)_args.push(arguments[j++]);
    return invoke(fn, _args, that);
  }
}

function ctx(fn, that, length){
  assertFunction(fn);
  if(~length && that === undefined)return fn;
  switch(length){
    case 1: return function(a){
      return fn.call(that, a);
    }
    case 2: return function(a, b){
      return fn.call(that, a, b);
    }
    case 3: return function(a, b, c){
      return fn.call(that, a, b, c);
    }
  } return function(){
      return fn.apply(that, arguments);
  }
}


function invoke(fn, args, that){
  var un = that === undefined;
  switch(args.length | 0){
    case 0: return un ? fn()
                      : fn.call(that);
    case 1: return un ? fn(args[0])
                      : fn.call(that, args[0]);
    case 2: return un ? fn(args[0], args[1])
                      : fn.call(that, args[0], args[1]);
    case 3: return un ? fn(args[0], args[1], args[2])
                      : fn.call(that, args[0], args[1], args[2]);
    case 4: return un ? fn(args[0], args[1], args[2], args[3])
                      : fn.call(that, args[0], args[1], args[2], args[3]);
    case 5: return un ? fn(args[0], args[1], args[2], args[3], args[4])
                      : fn.call(that, args[0], args[1], args[2], args[3], args[4]);
  } return              fn.apply(that, args);
}
function construct(target, argumentsList ){
  var proto    = assertFunction(arguments.length < 3 ? target : arguments[2])[PROTOTYPE]
    , instance = create(isObject(proto) ? proto : ObjectProto)
    , result   = apply.call(target, instance, argumentsList);
  return isObject(result) ? result : instance;
}


var create           = Object.create
  , getPrototypeOf   = Object.getPrototypeOf
  , setPrototypeOf   = Object.setPrototypeOf
  , defineProperty   = Object.defineProperty
  , defineProperties = Object.defineProperties
  , getOwnDescriptor = Object.getOwnPropertyDescriptor
  , getKeys          = Object.keys
  , getNames         = Object.getOwnPropertyNames
  , getSymbols       = Object.getOwnPropertySymbols
  , isFrozen         = Object.isFrozen
  , has              = ctx(call, ObjectProto[HAS_OWN], 2)
 
  , ES5Object        = Object
  , Dict;
function toObject(it){
  return ES5Object(assertDefined(it));
}
function returnIt(it){
  return it;
}
function returnThis(){
  return this;
}
function get(object, key){
  if(has(object, key))return object[key];
}
function ownKeys(it){
  assertObject(it);
  return getSymbols ? getNames(it).concat(getSymbols(it)) : getNames(it);
}

var assign = Object.assign || function(target, source){
  var T = Object(assertDefined(target))
    , l = arguments.length
    , i = 1;
  while(l > i){
    var S      = ES5Object(arguments[i++])
      , keys   = getKeys(S)
      , length = keys.length
      , j      = 0
      , key;
    while(length > j)T[key = keys[j++]] = S[key];
  }
  return T;
}
function keyOf(object, el){
  var O      = toObject(object)
    , keys   = getKeys(O)
    , length = keys.length
    , index  = 0
    , key;
  while(length > index)if(O[key = keys[index++]] === el)return key;
}



function array(it){
  return String(it).split(',');
}
var push    = ArrayProto.push
  , unshift = ArrayProto.unshift
  , slice   = ArrayProto.slice
  , splice  = ArrayProto.splice
  , indexOf = ArrayProto.indexOf
  , forEach = ArrayProto[FOR_EACH];

function createArrayMethod(type){
  var isMap       = type == 1
    , isFilter    = type == 2
    , isSome      = type == 3
    , isEvery     = type == 4
    , isFindIndex = type == 6
    , noholes     = type == 5 || isFindIndex;
  return function(callbackfn){
    var O      = Object(assertDefined(this))
      , that   = arguments[1]
      , self   = ES5Object(O)
      , f      = ctx(callbackfn, that, 3)
      , length = toLength(self.length)
      , index  = 0
      , result = isMap ? Array(length) : isFilter ? [] : undefined
      , val, res;
    for(;length > index; index++)if(noholes || index in self){
      val = self[index];
      res = f(val, index, O);
      if(type){
        if(isMap)result[index] = res;            
        else if(res)switch(type){
          case 3: return true;                   
          case 5: return val;                    
          case 6: return index;                  
          case 2: result.push(val);              
        } else if(isEvery)return false;          
      }
    }
    return isFindIndex ? -1 : isSome || isEvery ? isEvery : result;
  }
}
function createArrayContains(isContains){
  return function(el ){
    var O      = toObject(this)
      , length = toLength(O.length)
      , index  = toIndex(arguments[1], length);
    if(isContains && el != el){
      for(;length > index; index++)if(sameNaN(O[index]))return isContains || index;
    } else for(;length > index; index++)if(isContains || index in O){
      if(O[index] === el)return isContains || index;
    } return !isContains && -1;
  }
}
function generic(A, B){
 
  return typeof A == 'function' ? A : B;
}


var MAX_SAFE_INTEGER = 0x1fffffffffffff
  , pow    = Math.pow
  , abs    = Math.abs
  , ceil   = Math.ceil
  , floor  = Math.floor
  , max    = Math.max
  , min    = Math.min
  , random = Math.random
  , trunc  = Math.trunc || function(it){
      return (it > 0 ? floor : ceil)(it);
    }

function sameNaN(number){
  return number != number;
}

function toInteger(it){
  return isNaN(it) ? 0 : trunc(it);
}

function toLength(it){
  return it > 0 ? min(toInteger(it), MAX_SAFE_INTEGER) : 0;
}
function toIndex(index, length){
  var index = toInteger(index);
  return index < 0 ? max(index + length, 0) : min(index, length);
}
function lz(num){
  return num > 9 ? num : '0' + num;
}

function createReplacer(regExp, replace, isStatic){
  var replacer = isObject(replace) ? function(part){
    return replace[part];
  } : replace;
  return function(it){
    return String(isStatic ? it : this).replace(regExp, replacer);
  }
}
function createPointAt(toString){
  return function(pos){
    var s = String(assertDefined(this))
      , i = toInteger(pos)
      , l = s.length
      , a, b;
    if(i < 0 || i >= l)return toString ? '' : undefined;
    a = s.charCodeAt(i);
    return a < 0xd800 || a > 0xdbff || i + 1 === l || (b = s.charCodeAt(i + 1)) < 0xdc00 || b > 0xdfff
      ? toString ? s.charAt(i) : a
      : toString ? s.slice(i, i + 2) : (a - 0xd800 << 10) + (b - 0xdc00) + 0x10000;
  }
}


var REDUCE_ERROR = 'Reduce of empty object with no initial value';
function assert(condition, msg1, msg2){
  if(!condition)throw TypeError(msg2 ? msg1 + msg2 : msg1);
}
function assertDefined(it){
  if(it == undefined)throw TypeError('Function called on null or undefined');
  return it;
}
function assertFunction(it){
  assert(isFunction(it), it, ' is not a function!');
  return it;
}
function assertObject(it){
  assert(isObject(it), it, ' is not an object!');
  return it;
}
function assertInstance(it, Constructor, name){
  assert(it instanceof Constructor, name, ": use the 'new' operator!");
}


function descriptor(bitmap, value){
  return {
    enumerable  : !(bitmap & 1),
    configurable: !(bitmap & 2),
    writable    : !(bitmap & 4),
    value       : value
  }
}
function simpleSet(object, key, value){
  object[key] = value;
  return object;
}
function createDefiner(bitmap){
  return DESC ? function(object, key, value){
    return defineProperty(object, key, descriptor(bitmap, value));
  } : simpleSet;
}
function uid(key){
  return SYMBOL + '(' + key + ')_' + (++sid + random())[TO_STRING](36);
}
function getWellKnownSymbol(name, setter){
  return (Symbol && Symbol[name]) || (setter ? Symbol : safeSymbol)(SYMBOL + DOT + name);
}

var DESC = !!function(){
      try {
        return defineProperty({}, 'a', {get: function(){ return 2 }}).a == 2;
      } catch(e){}
    }()
  , sid    = 0
  , hidden = createDefiner(1)
  , set    = Symbol ? simpleSet : hidden
  , safeSymbol = Symbol || uid;
function assignHidden(target, src){
  for(var key in src)hidden(target, key, src[key]);
  return target;
}

var SYMBOL_UNSCOPABLES = getWellKnownSymbol('unscopables')
  , ArrayUnscopables   = ArrayProto[SYMBOL_UNSCOPABLES] || {}
  , SYMBOL_SPECIES     = getWellKnownSymbol('species');
function setSpecies(C){
  if(framework || !isNative(C))defineProperty(C, SYMBOL_SPECIES, {
    configurable: true,
    get: returnThis
  });
}


var SYMBOL_ITERATOR = getWellKnownSymbol(ITERATOR)
  , SYMBOL_TAG      = getWellKnownSymbol(TO_STRING_TAG)
  , SUPPORT_FF_ITER = FF_ITERATOR in ArrayProto
  , ITER  = safeSymbol('iter')
  , KEY   = 1
  , VALUE = 2
  , Iterators = {}
  , IteratorPrototype = {}
  , NATIVE_ITERATORS = SYMBOL_ITERATOR in ArrayProto
   
  , BUGGY_ITERATORS = 'keys' in ArrayProto && !('next' in [].keys());

setIterator(IteratorPrototype, returnThis);
function setIterator(O, value){
  hidden(O, SYMBOL_ITERATOR, value);
 
  SUPPORT_FF_ITER && hidden(O, FF_ITERATOR, value);
}
function createIterator(Constructor, NAME, next, proto){
  Constructor[PROTOTYPE] = create(proto || IteratorPrototype, {next: descriptor(1, next)});
  setToStringTag(Constructor, NAME + ' Iterator');
}
function defineIterator(Constructor, NAME, value, DEFAULT){
  var proto = Constructor[PROTOTYPE]
    , iter  = get(proto, SYMBOL_ITERATOR) || get(proto, FF_ITERATOR) || (DEFAULT && get(proto, DEFAULT)) || value;
  if(framework){
   
    setIterator(proto, iter);
    if(iter !== value){
      var iterProto = getPrototypeOf(iter.call(new Constructor));
     
      setToStringTag(iterProto, NAME + ' Iterator', true);
     
      has(proto, FF_ITERATOR) && setIterator(iterProto, returnThis);
    }
  }
 
  Iterators[NAME] = iter;
 
  Iterators[NAME + ' Iterator'] = returnThis;
  return iter;
}
function defineStdIterators(Base, NAME, Constructor, next, DEFAULT, IS_SET){
  function createIter(kind){
    return function(){
      return new Constructor(this, kind);
    }
  }
  createIterator(Constructor, NAME, next);
  var entries = createIter(KEY+VALUE)
    , values  = createIter(VALUE);
  if(DEFAULT == VALUE)values = defineIterator(Base, NAME, values, 'values');
  else entries = defineIterator(Base, NAME, entries, 'entries');
  if(DEFAULT){
    $define(PROTO + FORCED * BUGGY_ITERATORS, NAME, {
      entries: entries,
      keys: IS_SET ? values : createIter(KEY),
      values: values
    });
  }
}
function iterResult(done, value){
  return {value: value, done: !!done};
}
function isIterable(it){
  var O      = Object(it)
    , Symbol = global[SYMBOL]
    , hasExt = (Symbol && Symbol[ITERATOR] || FF_ITERATOR) in O;
  return hasExt || SYMBOL_ITERATOR in O || has(Iterators, classof(O));
}
function getIterator(it){
  var Symbol  = global[SYMBOL]
    , ext     = it[Symbol && Symbol[ITERATOR] || FF_ITERATOR]
    , getIter = ext || it[SYMBOL_ITERATOR] || Iterators[classof(it)];
  return assertObject(getIter.call(it));
}
function stepCall(fn, value, entries){
  return entries ? invoke(fn, value) : fn(value);
}
function forOf(iterable, entries, fn, that){
  var iterator = getIterator(iterable)
    , f        = ctx(fn, that, entries ? 2 : 1)
    , step;
  while(!(step = iterator.next()).done)if(stepCall(f, step.value, entries) === false)return;
}


var NODE = cof(process) == PROCESS
  , core = {}
  , path = framework ? global : core
  , old  = global.core
  , exportGlobal
 
  , FORCED = 1
  , GLOBAL = 2
  , STATIC = 4
  , PROTO  = 8
  , BIND   = 16
  , WRAP   = 32
  , SIMPLE = 64;
function $define(type, name, source){
  var key, own, out, exp
    , isGlobal = type & GLOBAL
    , target   = isGlobal ? global : (type & STATIC)
        ? global[name] : (global[name] || ObjectProto)[PROTOTYPE]
    , exports  = isGlobal ? core : core[name] || (core[name] = {});
  if(isGlobal)source = name;
  for(key in source){
   
    own = !(type & FORCED) && target && key in target
      && (!isFunction(target[key]) || isNative(target[key]));
   
    out = (own ? target : source)[key];
   
    if(!framework && isGlobal && !isFunction(target[key]))exp = source[key];
   
    else if(type & BIND && own)exp = ctx(out, global);
   
    else if(type & WRAP && !framework && target[key] == out){
      exp = function(param){
        return this instanceof out ? new out(param) : out(param);
      }
      exp[PROTOTYPE] = out[PROTOTYPE];
    } else exp = type & PROTO && isFunction(out) ? ctx(call, out) : out;
   
    if(framework && target && !own){
      if(isGlobal || type & SIMPLE)target[key] = out;
      else delete target[key] && hidden(target, key, out);
    }
   
    if(exports[key] != out)hidden(exports, key, exp);
  }
}

if(typeof module != 'undefined' && module.exports)module.exports = core;

else if(isFunction(define) && define.amd)define(function(){return core});

else exportGlobal = true;
if(exportGlobal || framework){
  core.noConflict = function(){
    global.core = old;
    return core;
  }
  global.core = core;
}




!function(TAG, SymbolRegistry, AllSymbols, setter){
 
  if(!isNative(Symbol)){
    Symbol = function(description){
      assert(!(this instanceof Symbol), SYMBOL + ' is not a ' + CONSTRUCTOR);
      var tag = uid(description)
        , sym = set(create(Symbol[PROTOTYPE]), TAG, tag);
      AllSymbols[tag] = sym;
      DESC && setter && defineProperty(ObjectProto, tag, {
        configurable: true,
        set: function(value){
          hidden(this, tag, value);
        }
      });
      return sym;
    }
    hidden(Symbol[PROTOTYPE], TO_STRING, function(){
      return this[TAG];
    });
  }
  $define(GLOBAL + WRAP, {Symbol: Symbol});
  
  var symbolStatics = {
   
    'for': function(key){
      return has(SymbolRegistry, key += '')
        ? SymbolRegistry[key]
        : SymbolRegistry[key] = Symbol(key);
    },
   
    iterator: SYMBOL_ITERATOR,
   
    keyFor: part.call(keyOf, SymbolRegistry),
   
    species: SYMBOL_SPECIES,
   
    toStringTag: SYMBOL_TAG = getWellKnownSymbol(TO_STRING_TAG, true),
   
    unscopables: SYMBOL_UNSCOPABLES,
    pure: safeSymbol,
    set: set,
    useSetter: function(){setter = true},
    useSimple: function(){setter = false}
  };
 
 
 
 
 
 
 
  forEach.call(array('hasInstance,isConcatSpreadable,match,replace,search,split,toPrimitive'),
    function(it){
      symbolStatics[it] = getWellKnownSymbol(it);
    }
  );
  $define(STATIC, SYMBOL, symbolStatics);
  
  setToStringTag(Symbol, SYMBOL);
  
  $define(STATIC + FORCED * !isNative(Symbol), OBJECT, {
   
    getOwnPropertyNames: function(it){
      var names = getNames(toObject(it)), result = [], key, i = 0;
      while(names.length > i)has(AllSymbols, key = names[i++]) || result.push(key);
      return result;
    },
   
    getOwnPropertySymbols: function(it){
      var names = getNames(toObject(it)), result = [], key, i = 0;
      while(names.length > i)has(AllSymbols, key = names[i++]) && result.push(AllSymbols[key]);
      return result;
    }
  });
}(safeSymbol('tag'), {}, {}, true);



!function(at){
 
 
 
 
  defineStdIterators(Array, ARRAY, function(iterated, kind){
    set(this, ITER, {o: toObject(iterated), i: 0, k: kind});
 
  }, function(){
    var iter  = this[ITER]
      , O     = iter.o
      , kind  = iter.k
      , index = iter.i++;
    if(!O || index >= O.length){
      iter.o = undefined;
      return iterResult(1);
    }
    if(kind == KEY)  return iterResult(0, index);
    if(kind == VALUE)return iterResult(0, O[index]);
                     return iterResult(0, [index, O[index]]);
  }, VALUE);
  
 
  Iterators[ARGUMENTS] = Iterators[ARRAY];
  
 
  defineStdIterators(String, STRING, function(iterated){
    set(this, ITER, {o: String(iterated), i: 0});
 
  }, function(){
    var iter  = this[ITER]
      , O     = iter.o
      , index = iter.i
      , point;
    if(index >= O.length)return iterResult(1);
    point = at.call(O, index);
    iter.i += point.length;
    return iterResult(0, point);
  });
}(createPointAt(true));





isFunction(setImmediate) && isFunction(clearImmediate) || function(ONREADYSTATECHANGE){
  var postMessage      = global.postMessage
    , addEventListener = global.addEventListener
    , MessageChannel   = global.MessageChannel
    , counter          = 0
    , queue            = {}
    , defer, channel, port;
  setImmediate = function(fn){
    var args = [], i = 1;
    while(arguments.length > i)args.push(arguments[i++]);
    queue[++counter] = function(){
      invoke(isFunction(fn) ? fn : Function(fn), args);
    }
    defer(counter);
    return counter;
  }
  clearImmediate = function(id){
    delete queue[id];
  }
  function run(id){
    if(has(queue, id)){
      var fn = queue[id];
      delete queue[id];
      fn();
    }
  }
  function listner(event){
    run(event.data);
  }
 
  if(NODE){
    defer = function(id){
      nextTick(part.call(run, id));
    }
 
 
  } else if(global.addEventListener && isFunction(postMessage) && !global.importScripts){
    defer = function(id){
      postMessage(id, '*');
    }
    global.addEventListener('message', listner, false);
 
  } else if(isFunction(MessageChannel)){
    channel = new MessageChannel;
    port    = channel.port2;
    channel.port1.onmessage = listner;
    defer = ctx(port.postMessage, port, 1);
 
  } else if(document && ONREADYSTATECHANGE in document[CREATE_ELEMENT]('script')){
    defer = function(id){
      html.appendChild(document[CREATE_ELEMENT]('script'))[ONREADYSTATECHANGE] = function(){
        html.removeChild(this);
        run(id);
      }
    }
 
  } else {
    defer = function(id){
      setTimeout(run, 0, id);
    }
  }
}('onreadystatechange');
$define(GLOBAL + BIND, {
  setImmediate:   setImmediate,
  clearImmediate: clearImmediate
});





!function(Promise, test){
  isFunction(Promise) && isFunction(Promise.resolve)
  && Promise.resolve(test = new Promise(function(){})) == test
  || function(asap, DEF){
    function isThenable(o){
      var then;
      if(isObject(o))then = o.then;
      return isFunction(then) ? then : false;
    }
    function notify(def){
      var chain = def.chain;
      chain.length && asap(function(){
        var msg = def.msg
          , ok  = def.state == 1
          , i   = 0;
        while(chain.length > i)!function(react){
          var cb = ok ? react.ok : react.fail
            , ret, then;
          try {
            if(cb){
              ret = cb === true ? msg : cb(msg);
              if(ret === react.P){
                react.rej(TypeError(PROMISE + '-chain cycle'));
              } else if(then = isThenable(ret)){
                then.call(ret, react.res, react.rej);
              } else react.res(ret);
            } else react.rej(msg);
          } catch(err){
            react.rej(err);
          }
        }(chain[i++]);
        chain.length = 0;
      });
    }
    function resolve(msg){
      var def = this
        , then, wrapper;
      if(def.done)return;
      def.done = true;
      def = def.def || def;
      try {
        if(then = isThenable(msg)){
          wrapper = {def: def, done: false};
          then.call(msg, ctx(resolve, wrapper, 1), ctx(reject, wrapper, 1));
        } else {
          def.msg = msg;
          def.state = 1;
          notify(def);
        }
      } catch(err){
        reject.call(wrapper || {def: def, done: false}, err);
      }
    }
    function reject(msg){
      var def = this;
      if(def.done)return;
      def.done = true;
      def = def.def || def;
      def.msg = msg;
      def.state = 2;
      notify(def);
    }
    function getConstructor(C){
      var S = assertObject(C)[SYMBOL_SPECIES];
      return S != undefined ? S : C;
    }
   
    Promise = function(executor){
      assertFunction(executor);
      assertInstance(this, Promise, PROMISE);
      var def = {chain: [], state: 0, done: false, msg: undefined};
      hidden(this, DEF, def);
      try {
        executor(ctx(resolve, def, 1), ctx(reject, def, 1));
      } catch(err){
        reject.call(def, err);
      }
    }
    assignHidden(Promise[PROTOTYPE], {
     
      then: function(onFulfilled, onRejected){
        var S = assertObject(assertObject(this)[CONSTRUCTOR])[SYMBOL_SPECIES];
        var react = {
          ok:   isFunction(onFulfilled) ? onFulfilled : true,
          fail: isFunction(onRejected)  ? onRejected  : false
        } , P = react.P = new (S != undefined ? S : Promise)(function(resolve, reject){
          react.res = assertFunction(resolve);
          react.rej = assertFunction(reject);
        }), def = this[DEF];
        def.chain.push(react);
        def.state && notify(def);
        return P;
      },
     
      'catch': function(onRejected){
        return this.then(undefined, onRejected);
      }
    });
    assignHidden(Promise, {
     
      all: function(iterable){
        var Promise = getConstructor(this)
          , values  = [];
        return new Promise(function(resolve, reject){
          forOf(iterable, false, push, values);
          var remaining = values.length
            , results   = Array(remaining);
          if(remaining)forEach.call(values, function(promise, index){
            Promise.resolve(promise).then(function(value){
              results[index] = value;
              --remaining || resolve(results);
            }, reject);
          });
          else resolve(results);
        });
      },
     
      race: function(iterable){
        var Promise = getConstructor(this);
        return new Promise(function(resolve, reject){
          forOf(iterable, false, function(promise){
            Promise.resolve(promise).then(resolve, reject);
          });
        });
      },
     
      reject: function(r){
        return new (getConstructor(this))(function(resolve, reject){
          reject(r);
        });
      },
     
      resolve: function(x){
        return isObject(x) && DEF in x && getPrototypeOf(x) === this[PROTOTYPE]
          ? x : new (getConstructor(this))(function(resolve, reject){
            resolve(x);
          });
      }
    });
  }(nextTick || setImmediate, safeSymbol('def'));
  setToStringTag(Promise, PROMISE);
  setSpecies(Promise);
  $define(GLOBAL + FORCED * !isNative(Promise), {Promise: Promise});
}(global[PROMISE]);




!function(){
  var UID   = safeSymbol('uid')
    , O1    = safeSymbol('O1')
    , WEAK  = safeSymbol('weak')
    , LEAK  = safeSymbol('leak')
    , LAST  = safeSymbol('last')
    , FIRST = safeSymbol('first')
    , SIZE  = DESC ? safeSymbol('size') : 'size'
    , uid   = 0
    , tmp   = {};
  
  function getCollection(C, NAME, methods, commonMethods, isMap, isWeak){
    var ADDER = isMap ? 'set' : 'add'
      , proto = C && C[PROTOTYPE]
      , O     = {};
    function initFromIterable(that, iterable){
      if(iterable != undefined)forOf(iterable, isMap, that[ADDER], that);
      return that;
    }
    function fixSVZ(key, chain){
      var method = proto[key];
      if(framework)proto[key] = function(a, b){
        var result = method.call(this, a === 0 ? 0 : a, b);
        return chain ? this : result;
      };
    }
    if(!isNative(C) || !(isWeak || (!BUGGY_ITERATORS && has(proto, FOR_EACH) && has(proto, 'entries')))){
     
      C = isWeak
        ? function(iterable){
            assertInstance(this, C, NAME);
            set(this, UID, uid++);
            initFromIterable(this, iterable);
          }
        : function(iterable){
            var that = this;
            assertInstance(that, C, NAME);
            set(that, O1, create(null));
            set(that, SIZE, 0);
            set(that, LAST, undefined);
            set(that, FIRST, undefined);
            initFromIterable(that, iterable);
          };
      assignHidden(assignHidden(C[PROTOTYPE], methods), commonMethods);
      isWeak || defineProperty(C[PROTOTYPE], 'size', {get: function(){
        return assertDefined(this[SIZE]);
      }});
    } else {
      var Native = C
        , inst   = new C
        , chain  = inst[ADDER](isWeak ? {} : -0, 1)
        , buggyZero;
     
      if(!NATIVE_ITERATORS || !C.length){
        C = function(iterable){
          assertInstance(this, C, NAME);
          return initFromIterable(new Native, iterable);
        }
        C[PROTOTYPE] = proto;
        if(framework)proto[CONSTRUCTOR] = C;
      }
      isWeak || inst[FOR_EACH](function(val, key){
        buggyZero = 1 / key === -Infinity;
      });
     
      if(buggyZero){
        fixSVZ('delete');
        fixSVZ('has');
        isMap && fixSVZ('get');
      }
     
      if(buggyZero || chain !== inst)fixSVZ(ADDER, true);
    }
    setToStringTag(C, NAME);
    setSpecies(C);
    
    O[NAME] = C;
    $define(GLOBAL + WRAP + FORCED * !isNative(C), O);
    
   
   
    isWeak || defineStdIterators(C, NAME, function(iterated, kind){
      set(this, ITER, {o: iterated, k: kind});
    }, function(){
      var iter  = this[ITER]
        , kind  = iter.k
        , entry = iter.l;
     
      while(entry && entry.r)entry = entry.p;
     
      if(!iter.o || !(iter.l = entry = entry ? entry.n : iter.o[FIRST])){
       
        iter.o = undefined;
        return iterResult(1);
      }
     
      if(kind == KEY)  return iterResult(0, entry.k);
      if(kind == VALUE)return iterResult(0, entry.v);
                       return iterResult(0, [entry.k, entry.v]);   
    }, isMap ? KEY+VALUE : VALUE, !isMap);
    
    return C;
  }
  
  function fastKey(it, create){
   
    if(!isObject(it))return (typeof it == 'string' ? 'S' : 'P') + it;
   
    if(isFrozen(it))return 'F';
    if(!has(it, UID)){
     
      if(!create)return 'E';
     
      hidden(it, UID, ++uid);
   
    } return 'O' + it[UID];
  }
  function getEntry(that, key){
   
    var index = fastKey(key), entry;
    if(index != 'F')return that[O1][index];
   
    for(entry = that[FIRST]; entry; entry = entry.n){
      if(entry.k == key)return entry;
    }
  }
  function def(that, key, value){
    var entry = getEntry(that, key)
      , prev, index;
   
    if(entry)entry.v = value;
   
    else {
      that[LAST] = entry = {
        i: index = fastKey(key, true),
        k: key,                       
        v: value,                     
        p: prev = that[LAST],         
        n: undefined,                 
        r: false                      
      };
      if(!that[FIRST])that[FIRST] = entry;
      if(prev)prev.n = entry;
      that[SIZE]++;
     
      if(index != 'F')that[O1][index] = entry;
    } return that;
  }

  var collectionMethods = {
   
   
    clear: function(){
      for(var that = this, data = that[O1], entry = that[FIRST]; entry; entry = entry.n){
        entry.r = true;
        if(entry.p)entry.p = entry.p.n = undefined;
        delete data[entry.i];
      }
      that[FIRST] = that[LAST] = undefined;
      that[SIZE] = 0;
    },
   
   
    'delete': function(key){
      var that  = this
        , entry = getEntry(that, key);
      if(entry){
        var next = entry.n
          , prev = entry.p;
        delete that[O1][entry.i];
        entry.r = true;
        if(prev)prev.n = next;
        if(next)next.p = prev;
        if(that[FIRST] == entry)that[FIRST] = next;
        if(that[LAST] == entry)that[LAST] = prev;
        that[SIZE]--;
      } return !!entry;
    },
   
   
    forEach: function(callbackfn ){
      var f = ctx(callbackfn, arguments[1], 3)
        , entry;
      while(entry = entry ? entry.n : this[FIRST]){
        f(entry.v, entry.k, this);
       
        while(entry && entry.r)entry = entry.p;
      }
    },
   
   
    has: function(key){
      return !!getEntry(this, key);
    }
  }
  
 
  Map = getCollection(Map, MAP, {
   
    get: function(key){
      var entry = getEntry(this, key);
      return entry && entry.v;
    },
   
    set: function(key, value){
      return def(this, key === 0 ? 0 : key, value);
    }
  }, collectionMethods, true);
  
 
  Set = getCollection(Set, SET, {
   
    add: function(value){
      return def(this, value = value === 0 ? 0 : value, value);
    }
  }, collectionMethods);
  
  function defWeak(that, key, value){
    if(isFrozen(assertObject(key)))leakStore(that).set(key, value);
    else {
      has(key, WEAK) || hidden(key, WEAK, {});
      key[WEAK][that[UID]] = value;
    } return that;
  }
  function leakStore(that){
    return that[LEAK] || hidden(that, LEAK, new Map)[LEAK];
  }
  
  var weakMethods = {
   
   
    'delete': function(key){
      if(!isObject(key))return false;
      if(isFrozen(key))return leakStore(this)['delete'](key);
      return has(key, WEAK) && has(key[WEAK], this[UID]) && delete key[WEAK][this[UID]];
    },
   
   
    has: function(key){
      if(!isObject(key))return false;
      if(isFrozen(key))return leakStore(this).has(key);
      return has(key, WEAK) && has(key[WEAK], this[UID]);
    }
  };
  
 
  WeakMap = getCollection(WeakMap, WEAKMAP, {
   
    get: function(key){
      if(isObject(key)){
        if(isFrozen(key))return leakStore(this).get(key);
        if(has(key, WEAK))return key[WEAK][this[UID]];
      }
    },
   
    set: function(key, value){
      return defWeak(this, key, value);
    }
  }, weakMethods, true, true);
  
 
  if(framework && new WeakMap().set(Object.freeze(tmp), 7).get(tmp) != 7){
    forEach.call(array('delete,has,get,set'), function(key){
      var method = WeakMap[PROTOTYPE][key];
      WeakMap[PROTOTYPE][key] = function(a, b){
       
        if(isObject(a) && isFrozen(a)){
          var result = leakStore(this)[key](a, b);
          return key == 'set' ? this : result;
       
        } return method.call(this, a, b);
      };
    });
  }
  
 
  WeakSet = getCollection(WeakSet, WEAKSET, {
   
    add: function(value){
      return defWeak(this, value, true);
    }
  }, weakMethods, false, true);
}();
}(typeof self != 'undefined' && self.Math === Math ? self : Function('return this')(), true);


!(function(global) {
  "use strict";

  var hasOwn = Object.prototype.hasOwnProperty;
  var undefined;
  var iteratorSymbol =
    typeof Symbol === "function" && Symbol.iterator || "@@iterator";

  var inModule = typeof module === "object";
  var runtime = global.regeneratorRuntime;
  if (runtime) {
    if (inModule) {
     
     
      module.exports = runtime;
    }
   
   
    return;
  }

 
 
  runtime = global.regeneratorRuntime = inModule ? module.exports : {};

  function wrap(innerFn, outerFn, self, tryLocsList) {
    return new Generator(innerFn, outerFn, self || null, tryLocsList || []);
  }
  runtime.wrap = wrap;

 
 
 
 
 
 
 
 
 
 
  function tryCatch(fn, obj, arg) {
    try {
      return { type: "normal", arg: fn.call(obj, arg) };
    } catch (err) {
      return { type: "throw", arg: err };
    }
  }

  var GenStateSuspendedStart = "suspendedStart";
  var GenStateSuspendedYield = "suspendedYield";
  var GenStateExecuting = "executing";
  var GenStateCompleted = "completed";

 
 
  var ContinueSentinel = {};

 
 
 
 
  function GeneratorFunction() {}
  function GeneratorFunctionPrototype() {}

  var Gp = GeneratorFunctionPrototype.prototype = Generator.prototype;
  GeneratorFunction.prototype = Gp.constructor = GeneratorFunctionPrototype;
  GeneratorFunctionPrototype.constructor = GeneratorFunction;
  GeneratorFunction.displayName = "GeneratorFunction";

  runtime.isGeneratorFunction = function(genFun) {
    var ctor = typeof genFun === "function" && genFun.constructor;
    return ctor
      ? ctor === GeneratorFunction ||
       
       
        (ctor.displayName || ctor.name) === "GeneratorFunction"
      : false;
  };

  runtime.mark = function(genFun) {
    genFun.__proto__ = GeneratorFunctionPrototype;
    genFun.prototype = Object.create(Gp);
    return genFun;
  };

  runtime.async = function(innerFn, outerFn, self, tryLocsList) {
    return new Promise(function(resolve, reject) {
      var generator = wrap(innerFn, outerFn, self, tryLocsList);
      var callNext = step.bind(generator.next);
      var callThrow = step.bind(generator["throw"]);

      function step(arg) {
        var record = tryCatch(this, null, arg);
        if (record.type === "throw") {
          reject(record.arg);
          return;
        }

        var info = record.arg;
        if (info.done) {
          resolve(info.value);
        } else {
          Promise.resolve(info.value).then(callNext, callThrow);
        }
      }

      callNext();
    });
  };

  function Generator(innerFn, outerFn, self, tryLocsList) {
    var generator = outerFn ? Object.create(outerFn.prototype) : this;
    var context = new Context(tryLocsList);
    var state = GenStateSuspendedStart;

    function invoke(method, arg) {
      if (state === GenStateExecuting) {
        throw new Error("Generator is already running");
      }

      if (state === GenStateCompleted) {
       
       
        return doneResult();
      }

      while (true) {
        var delegate = context.delegate;
        if (delegate) {
          var record = tryCatch(
            delegate.iterator[method],
            delegate.iterator,
            arg
          );

          if (record.type === "throw") {
            context.delegate = null;

           
           
            method = "throw";
            arg = record.arg;

            continue;
          }

         
         
         
          method = "next";
          arg = undefined;

          var info = record.arg;
          if (info.done) {
            context[delegate.resultName] = info.value;
            context.next = delegate.nextLoc;
          } else {
            state = GenStateSuspendedYield;
            return info;
          }

          context.delegate = null;
        }

        if (method === "next") {
          if (state === GenStateSuspendedStart &&
              typeof arg !== "undefined") {
           
            throw new TypeError(
              "attempt to send " + JSON.stringify(arg) + " to newborn generator"
            );
          }

          if (state === GenStateSuspendedYield) {
            context.sent = arg;
          } else {
            delete context.sent;
          }

        } else if (method === "throw") {
          if (state === GenStateSuspendedStart) {
            state = GenStateCompleted;
            throw arg;
          }

          if (context.dispatchException(arg)) {
           
           
            method = "next";
            arg = undefined;
          }

        } else if (method === "return") {
          context.abrupt("return", arg);
        }

        state = GenStateExecuting;

        var record = tryCatch(innerFn, self, context);
        if (record.type === "normal") {
         
         
          state = context.done
            ? GenStateCompleted
            : GenStateSuspendedYield;

          var info = {
            value: record.arg,
            done: context.done
          };

          if (record.arg === ContinueSentinel) {
            if (context.delegate && method === "next") {
             
             
              arg = undefined;
            }
          } else {
            return info;
          }

        } else if (record.type === "throw") {
          state = GenStateCompleted;

          if (method === "next") {
            context.dispatchException(record.arg);
          } else {
            arg = record.arg;
          }
        }
      }
    }

    generator.next = invoke.bind(generator, "next");
    generator["throw"] = invoke.bind(generator, "throw");
    generator["return"] = invoke.bind(generator, "return");

    return generator;
  }

  Gp[iteratorSymbol] = function() {
    return this;
  };

  Gp.toString = function() {
    return "[object Generator]";
  };

  function pushTryEntry(locs) {
    var entry = { tryLoc: locs[0] };

    if (1 in locs) {
      entry.catchLoc = locs[1];
    }

    if (2 in locs) {
      entry.finallyLoc = locs[2];
      entry.afterLoc = locs[3];
    }

    this.tryEntries.push(entry);
  }

  function resetTryEntry(entry) {
    var record = entry.completion || {};
    record.type = "normal";
    delete record.arg;
    entry.completion = record;
  }

  function Context(tryLocsList) {
   
   
   
    this.tryEntries = [{ tryLoc: "root" }];
    tryLocsList.forEach(pushTryEntry, this);
    this.reset();
  }

  runtime.keys = function(object) {
    var keys = [];
    for (var key in object) {
      keys.push(key);
    }
    keys.reverse();

   
   
    return function next() {
      while (keys.length) {
        var key = keys.pop();
        if (key in object) {
          next.value = key;
          next.done = false;
          return next;
        }
      }

     
     
     
      next.done = true;
      return next;
    };
  };

  function values(iterable) {
    if (iterable) {
      var iteratorMethod = iterable[iteratorSymbol];
      if (iteratorMethod) {
        return iteratorMethod.call(iterable);
      }

      if (typeof iterable.next === "function") {
        return iterable;
      }

      if (!isNaN(iterable.length)) {
        var i = -1, next = function next() {
          while (++i < iterable.length) {
            if (hasOwn.call(iterable, i)) {
              next.value = iterable[i];
              next.done = false;
              return next;
            }
          }

          next.value = undefined;
          next.done = true;

          return next;
        };

        return next.next = next;
      }
    }

   
    return { next: doneResult };
  }
  runtime.values = values;

  function doneResult() {
    return { value: undefined, done: true };
  }

  Context.prototype = {
    constructor: Context,

    reset: function() {
      this.prev = 0;
      this.next = 0;
      this.sent = undefined;
      this.done = false;
      this.delegate = null;

      this.tryEntries.forEach(resetTryEntry);

     
     
      for (var tempIndex = 0, tempName;
           hasOwn.call(this, tempName = "t" + tempIndex) || tempIndex < 20;
           ++tempIndex) {
        this[tempName] = null;
      }
    },

    stop: function() {
      this.done = true;

      var rootEntry = this.tryEntries[0];
      var rootRecord = rootEntry.completion;
      if (rootRecord.type === "throw") {
        throw rootRecord.arg;
      }

      return this.rval;
    },

    dispatchException: function(exception) {
      if (this.done) {
        throw exception;
      }

      var context = this;
      function handle(loc, caught) {
        record.type = "throw";
        record.arg = exception;
        context.next = loc;
        return !!caught;
      }

      for (var i = this.tryEntries.length - 1; i >= 0; --i) {
        var entry = this.tryEntries[i];
        var record = entry.completion;

        if (entry.tryLoc === "root") {
         
         
         
          return handle("end");
        }

        if (entry.tryLoc <= this.prev) {
          var hasCatch = hasOwn.call(entry, "catchLoc");
          var hasFinally = hasOwn.call(entry, "finallyLoc");

          if (hasCatch && hasFinally) {
            if (this.prev < entry.catchLoc) {
              return handle(entry.catchLoc, true);
            } else if (this.prev < entry.finallyLoc) {
              return handle(entry.finallyLoc);
            }

          } else if (hasCatch) {
            if (this.prev < entry.catchLoc) {
              return handle(entry.catchLoc, true);
            }

          } else if (hasFinally) {
            if (this.prev < entry.finallyLoc) {
              return handle(entry.finallyLoc);
            }

          } else {
            throw new Error("try statement without catch or finally");
          }
        }
      }
    },

    abrupt: function(type, arg) {
      for (var i = this.tryEntries.length - 1; i >= 0; --i) {
        var entry = this.tryEntries[i];
        if (entry.tryLoc <= this.prev &&
            hasOwn.call(entry, "finallyLoc") &&
            this.prev < entry.finallyLoc) {
          var finallyEntry = entry;
          break;
        }
      }

      if (finallyEntry &&
          (type === "break" ||
           type === "continue") &&
          finallyEntry.tryLoc <= arg &&
          arg < finallyEntry.finallyLoc) {
       
       
        finallyEntry = null;
      }

      var record = finallyEntry ? finallyEntry.completion : {};
      record.type = type;
      record.arg = arg;

      if (finallyEntry) {
        this.next = finallyEntry.finallyLoc;
      } else {
        this.complete(record);
      }

      return ContinueSentinel;
    },

    complete: function(record, afterLoc) {
      if (record.type === "throw") {
        throw record.arg;
      }

      if (record.type === "break" ||
          record.type === "continue") {
        this.next = record.arg;
      } else if (record.type === "return") {
        this.rval = record.arg;
        this.next = "end";
      } else if (record.type === "normal" && afterLoc) {
        this.next = afterLoc;
      }

      return ContinueSentinel;
    },

    finish: function(finallyLoc) {
      for (var i = this.tryEntries.length - 1; i >= 0; --i) {
        var entry = this.tryEntries[i];
        if (entry.finallyLoc === finallyLoc) {
          return this.complete(entry.completion, entry.afterLoc);
        }
      }
    },

    "catch": function(tryLoc) {
      for (var i = this.tryEntries.length - 1; i >= 0; --i) {
        var entry = this.tryEntries[i];
        if (entry.tryLoc === tryLoc) {
          var record = entry.completion;
          if (record.type === "throw") {
            var thrown = record.arg;
            resetTryEntry(entry);
          }
          return thrown;
        }
      }

     
     
      throw new Error("illegal catch attempt");
    },

    delegateYield: function(iterable, resultName, nextLoc) {
      this.delegate = {
        iterator: values(iterable),
        resultName: resultName,
        nextLoc: nextLoc
      };

      return ContinueSentinel;
    }
  };
})(
 
 
 
  typeof global === "object" ? global :
  typeof window === "object" ? window : this
);
"use strict";

var _slicedToArray = function (arr, i) { if (Array.isArray(arr)) { return arr; } else if (Symbol.iterator in Object(arr)) { var _arr = []; for (var _iterator = arr[Symbol.iterator](), _step; !(_step = _iterator.next()).done;) { _arr.push(_step.value); if (i && _arr.length === i) break; } return _arr; } else { throw new TypeError("Invalid attempt to destructure non-iterable instance"); } };




















(function de_main_func_inner(scriptStorage, FormData) {
	"use strict";

	var getFormElements = regeneratorRuntime.mark(


	function getFormElements(form) {
		var controls, fixName, i, len, field, tagName, type, name, options, _i, _len, option, files, dirname, dir;

		return regeneratorRuntime.wrap(function getFormElements$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					controls = $Q("button, input, keygen, object, select, textarea", form), fixName = function (name) {
						return name ? name.replace(/([^\r])\n|\r([^\n])/g, "$1\r\n$2") : "";
					};
					i = 0, len = controls.length;

				case 2:
					if (!(i < len)) {
						context$2$0.next = 56;
						break;
					}

					field = controls[i], tagName = field.tagName.toLowerCase(), type = field.getAttribute("type"), name = field.getAttribute("name");

					if (!($parent(field, "DATALIST", form) || isFormElDisabled(field) || tagName === "button" && type !== "submit" || tagName === "input" && (type === "checkbox" && !field.checked || type === "radio" && !field.checked || type === "image" && !name) || tagName === "object")) {
						context$2$0.next = 6;
						break;
					}

					return context$2$0.abrupt("continue", 53);

				case 6:
					if (!(tagName === "select")) {
						context$2$0.next = 19;
						break;
					}

					options = $Q("select > option, select > optgrout > option", field);
					_i = 0, _len = options.length;

				case 9:
					if (!(_i < _len)) {
						context$2$0.next = 17;
						break;
					}

					option = options[_i];

					if (!(option.selected && !isFormElDisabled(option))) {
						context$2$0.next = 14;
						break;
					}

					context$2$0.next = 14;
					return {
						el: field,
						name: fixName(name),
						value: option.value,
						type: type
					};

				case 14:
					++_i;
					context$2$0.next = 9;
					break;

				case 17:
					context$2$0.next = 41;
					break;

				case 19:
					if (!(tagName === "input")) {
						context$2$0.next = 41;
						break;
					}

					context$2$0.t0 = type;
					context$2$0.next = context$2$0.t0 === "image" ? 23 : context$2$0.t0 === "checkbox" ? 24 : context$2$0.t0 === "radio" ? 24 : context$2$0.t0 === "file" ? 27 : 41;
					break;

				case 23:
					throw new Error("input[type=\"image\"] is not supported");

				case 24:
					context$2$0.next = 26;
					return {
						el: field,
						name: fixName(name),
						value: field.value || "on",
						type: type
					};

				case 26:
					return context$2$0.abrupt("continue", 53);

				case 27:
					if (!(field.files.length > 0)) {
						context$2$0.next = 38;
						break;
					}

					files = field.files;
					_i = 0, _len = files.length;

				case 30:
					if (!(_i < _len)) {
						context$2$0.next = 36;
						break;
					}

					context$2$0.next = 33;
					return {
						el: field,
						name: name,
						value: files[_i],
						type: type
					};

				case 33:
					++_i;
					context$2$0.next = 30;
					break;

				case 36:
					context$2$0.next = 40;
					break;

				case 38:
					context$2$0.next = 40;
					return {
						el: field,
						name: fixName(name),
						value: "",
						type: "application/octet-stream"
					};

				case 40:
					return context$2$0.abrupt("continue", 53);

				case 41:
					if (!(type === "textarea")) {
						context$2$0.next = 46;
						break;
					}

					context$2$0.next = 44;
					return {
						el: field,
						name: name || "",
						value: field.value,
						type: type
					};

				case 44:
					context$2$0.next = 48;
					break;

				case 46:
					context$2$0.next = 48;
					return {
						el: field,
						name: fixName(name),
						value: field.value,
						type: type
					};

				case 48:
					dirname = field.getAttribute("dirname");

					if (!dirname) {
						context$2$0.next = 53;
						break;
					}

					dir = nav.matchesSelector(field, ":dir(rtl)") ? "rtl" : "ltr";
					context$2$0.next = 53;
					return {
						el: field,
						name: fixName(dirname),
						value: dir,
						type: "direction"
					};

				case 53:
					++i;
					context$2$0.next = 2;
					break;

				case 56:
				case "end":
					return context$2$0.stop();
			}
		}, getFormElements, this);
	});
	var getStored = regeneratorRuntime.mark(




	function getStored(id) {
		return regeneratorRuntime.wrap(function getStored$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					if (!nav.isGM) {
						context$2$0.next = 4;
						break;
					}

					return context$2$0.abrupt("return", GM_getValue(id));

				case 4:
					if (!nav.isChromeStorage) {
						context$2$0.next = 10;
						break;
					}

					context$2$0.next = 7;
					return new Promise(function (resolve, reject) {
						chrome.storage.local.get(id, function (obj) {
							if (Object.keys(obj).length) {
								resolve(obj[id]);
							} else {
								chrome.storage.sync.get(id, function (obj) {
									resolve(obj[id]);
								});
							}
						});
					});

				case 7:
					return context$2$0.abrupt("return", context$2$0.sent);

				case 10:
					if (!nav.isScriptStorage) {
						context$2$0.next = 12;
						break;
					}

					return context$2$0.abrupt("return", scriptStorage.getItem(id));

				case 12:
					return context$2$0.abrupt("return", locStorage[id]);

				case 13:
				case "end":
					return context$2$0.stop();
			}
		}, getStored, this);
	});
	var getStoredObj = regeneratorRuntime.mark(function getStoredObj(id) {
		return regeneratorRuntime.wrap(function getStoredObj$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					return context$2$0.delegateYield(getStored(id), "t3", 1);

				case 1:
					context$2$0.t2 = context$2$0.t3;

					if (context$2$0.t2) {
						context$2$0.next = 4;
						break;
					}

					context$2$0.t2 = "{}";

				case 4:
					context$2$0.t1 = JSON.parse(context$2$0.t2);

					if (context$2$0.t1) {
						context$2$0.next = 7;
						break;
					}

					context$2$0.t1 = {};

				case 7:
					return context$2$0.abrupt("return", context$2$0.t1);

				case 8:
				case "end":
					return context$2$0.stop();
			}
		}, getStoredObj, this);
	});
	var readCfg = regeneratorRuntime.mark(function readCfg() {
		var obj, val;
		return regeneratorRuntime.wrap(function readCfg$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					return context$2$0.delegateYield(getStoredObj("DESU_Config"), "t4", 1);

				case 1:
					val = context$2$0.t4;

					if (!(aib.dm in val) || $isEmpty(obj = val[aib.dm])) {
						obj = nav.isGlobal ? val.global || {} : {};
						obj.captchaLang = aib.ru ? 2 : 1;
						obj.correctTime = 0;
					}
					Cfg = Object.assign(Object.create(defaultCfg), obj);
					if (!Cfg.timeOffset) {
						Cfg.timeOffset = "+0";
					}
					if (!Cfg.timePattern) {
						Cfg.timePattern = aib.timePattern;
					}
					if ((nav.Opera11 || aib.fch || aib.ponya) && Cfg.ajaxReply === 2) {
						Lng.cfg.ajaxReply.sel.forEach(function (a) {
							return a.splice(-1);
						});
						Cfg.ajaxReply = 1;
					}
					if (aib.tiny && !aib._8ch) {
						Cfg.fileThumb = 0;
					}
					if (aib.prot !== "http:") {
						Cfg.addVocaroo = 0;
					}
					if (!("Notification" in window)) {
						Cfg.desktNotif = 0;
					}
					if (nav.Presto) {
						if (nav.Opera11) {
							if (!nav.isGM) {
								Cfg.YTubeTitles = 0;
							}
							Cfg.animation = 0;
						}
						if (Cfg.YTubeType === 2) {
							Cfg.YTubeType = 1;
						}
						Cfg.preLoadImgs = 0;
						Cfg.findImgFile = 0;
						if (!nav.isGM) {
							Cfg.updScript = 0;
						}
						Cfg.fileThumb = 0;
					}
					if (nav.isChromeStorage) {
						Cfg.updScript = 0;
					}
					if (Cfg.updThrDelay < 10) {
						Cfg.updThrDelay = 10;
					}
					if (!Cfg.saveSage) {
						Cfg.sageReply = 0;
					}
					if (!Cfg.passwValue) {
						Cfg.passwValue = Math.round(Math.random() * 1000000000000000).toString(32);
					}
					if (!Cfg.stats) {
						Cfg.stats = { view: 0, op: 0, reply: 0 };
					}
					if (aib.t) {
						Cfg.stats.view++;
					}
					if (aib.fch) {
						Cfg.findImgFile = 0;
					}
					if (aib.synch) {
						Cfg.timePattern = "w+dd+m+yyyy+hh+ii+ss";
						Cfg.timeOffset = 4;
						Cfg.correctTime = 1;
					}
					setStored("DESU_Config", JSON.stringify(val));
					lang = Cfg.language;
					if (Cfg.updScript) {
						checkForUpdates(false, val.lastUpd).then(function (html) {
							if (doc.readyState === "interactive" || doc.readyState === "complete") {
								$alert(html, "updavail", false);
							} else {
								doc.addEventListener("DOMContentLoaded", function () {
									return $alert(html, "updavail", false);
								}, false);
							}
						}, emptyFn);
					}

				case 22:
				case "end":
					return context$2$0.stop();
			}
		}, readCfg, this);
	});
	var readUserPosts = regeneratorRuntime.mark(function readUserPosts() {
		var b, date, spellsHide, update, globalUserVis, sRunner, post, num, hidePost, hideThread, vis;
		return regeneratorRuntime.wrap(function readUserPosts$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					b = aib.b;
					date = Date.now();
					spellsHide = Cfg.hideBySpell;
					update = false;
					return context$2$0.delegateYield(getStoredObj("DESU_Posts_" + aib.dm), "t5", 5);

				case 5:
					globalUserVis = context$2$0.t5;
					return context$2$0.delegateYield(getStoredObj("DESU_Threads_" + aib.dm), "t6", 7);

				case 7:
					hThr = context$2$0.t6;

					uVis = globalUserVis[b] || {};
					if (!(b in hThr)) {
						hThr[b] = {};
					}

					if (dForm.firstThr) {
						context$2$0.next = 12;
						break;
					}

					return context$2$0.abrupt("return");

				case 12:
					sRunner = new SpellsRunner();
					post = dForm.firstThr.op;

				case 14:
					if (!post) {
						context$2$0.next = 34;
						break;
					}

					num = post.num;

					if (!(num in uVis)) {
						context$2$0.next = 21;
						break;
					}

					hidePost = uVis[num][0] === 0;

					if (post.isOp) {
						hideThread = !!(num in hThr[b]);

						if (hidePost !== hideThread) {
							update = true;
							hidePost = hideThread;
						}
					}
					if (hidePost) {
						post.setUserVisib(true, date, false);
					} else {
						uVis[num][1] = date;
						post.btns.firstChild.className = "de-btn-hide-user";
						post.userToggled = true;
					}
					return context$2$0.abrupt("continue", 31);

				case 21:
					if (!post.isOp) {
						context$2$0.next = 25;
						break;
					}

					if (num in hThr[b]) {
						vis = "0";
					} else if (vis === "0") {
						vis = null;
					}
					context$2$0.next = 30;
					break;

				case 25:
					if (!spellsHide) {
						context$2$0.next = 29;
						break;
					}

					vis = sVis[post.count];
					context$2$0.next = 30;
					break;

				case 29:
					return context$2$0.abrupt("continue", 31);

				case 30:
					if (vis === "0") {
						if (!post.hidden) {
							post.setVisib(true);
							post.hideRefs();
						}
						post.spellHidden = true;
					} else if (vis !== "1") {
						sRunner.run(post);
					}

				case 31:
					post = post.next;
					context$2$0.next = 14;
					break;

				case 34:
					if (update) {
						globalUserVis[b] = uVis;
						setStored("DESU_Posts_" + aib.dm, JSON.stringify(globalUserVis));
					}
					sRunner.end();

				case 36:
				case "end":
					return context$2$0.stop();
			}
		}, readUserPosts, this);
	});
	var readFavoritesPosts = regeneratorRuntime.mark(function readFavoritesPosts() {
		var temp, update, fav, thr, num;
		return regeneratorRuntime.wrap(function readFavoritesPosts$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					update = false;
					return context$2$0.delegateYield(getStoredObj("DESU_Favorites"), "t7", 2);

				case 2:
					fav = context$2$0.t7;

					if (aib.host in fav) {
						context$2$0.next = 5;
						break;
					}

					return context$2$0.abrupt("return");

				case 5:
					temp = fav[aib.host];

					if (aib.b in temp) {
						context$2$0.next = 8;
						break;
					}

					return context$2$0.abrupt("return");

				case 8:
					temp = temp[aib.b];
					for (thr = dForm.firstThr; thr; thr = thr.next) {
						num = thr.num;

						if (num in temp) {
							thr.setFavBtn(true);
							if (aib.t) {
								temp[num].cnt = thr.pcount;
								temp[num]["new"] = 0;
								temp[num].last = thr.last.num;
							} else {
								temp[num]["new"] = thr.pcount - temp[num].cnt;
							}
							update = true;
						}
					}
					if (update) {
						saveFavorites(fav);
					}

				case 11:
				case "end":
					return context$2$0.stop();
			}
		}, readFavoritesPosts, this);
	});
	var html5Submit = regeneratorRuntime.mark(function html5Submit(form) {
		var needProgress = arguments[1] === undefined ? false : arguments[1];

		var formData, hasFiles, _iteratorNormalCompletion, _didIteratorError, _iteratorError, _iterator, _step, _step$value, name, value, type, el, fileName, newFileName, data, lastFuncs, promises, xhr;

		return regeneratorRuntime.wrap(function html5Submit$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					formData = new FormData();
					hasFiles = false;
					_iteratorNormalCompletion = true;
					_didIteratorError = false;
					_iteratorError = undefined;
					context$2$0.prev = 5;
					_iterator = getFormElements(form)[Symbol.iterator]();

				case 7:
					if (_iteratorNormalCompletion = (_step = _iterator.next()).done) {
						context$2$0.next = 31;
						break;
					}

					_step$value = _step.value;
					name = _step$value.name;
					value = _step$value.value;
					type = _step$value.type;
					el = _step$value.el;

					if (!(type === "file")) {
						context$2$0.next = 27;
						break;
					}

					hasFiles = true;
					fileName = value.name, newFileName = Cfg.removeFName ? " " + fileName.substring(fileName.lastIndexOf(".")) : fileName;

					if (!(/^image\/(?:png|jpeg)$|^video\/webm$/.test(value.type) && (Cfg.postSameImg || Cfg.removeEXIF))) {
						context$2$0.next = 26;
						break;
					}

					context$2$0.next = 19;
					return readFileArrayBuffer(value);

				case 19:
					context$2$0.t25 = context$2$0.sent;
					data = cleanFile(context$2$0.t25, el.obj.imgFile);

					if (data) {
						context$2$0.next = 23;
						break;
					}

					return context$2$0.abrupt("return", Promise.reject(Lng.fileCorrupt[lang] + fileName));

				case 23:
					value = new File(data, newFileName);
					context$2$0.next = 27;
					break;

				case 26:
					if (Cfg.removeFName) {
						value = new File([value], newFileName);
					}

				case 27:
					formData.append(name, value);

				case 28:
					_iteratorNormalCompletion = true;
					context$2$0.next = 7;
					break;

				case 31:
					context$2$0.next = 37;
					break;

				case 33:
					context$2$0.prev = 33;
					context$2$0.t26 = context$2$0["catch"](5);
					_didIteratorError = true;
					_iteratorError = context$2$0.t26;

				case 37:
					context$2$0.prev = 37;
					context$2$0.prev = 38;

					if (!_iteratorNormalCompletion && _iterator["return"]) {
						_iterator["return"]();
					}

				case 40:
					context$2$0.prev = 40;

					if (!_didIteratorError) {
						context$2$0.next = 43;
						break;
					}

					throw _iteratorError;

				case 43:
					return context$2$0.finish(40);

				case 44:
					return context$2$0.finish(37);

				case 45:
					if (!needProgress) {
						context$2$0.next = 51;
						break;
					}

					lastFuncs = null, promises = [new Promise(function (resolve, reject) {
						return lastFuncs = { resolve: resolve, reject: reject };
					})];

					$ajax(form.action, { method: "POST", data: formData, onprogress: function (e) {
							lastFuncs.resolve({ done: false, data: { loaded: e.loaded, total: e.total } });
							promises.push(new Promise(function (resolve, reject) {
								return lastFuncs = { resolve: resolve, reject: reject };
							}));
						} }).then(function (xhr) {
						return lastFuncs.resolve({ done: true, data: $DOM(xhr.responseText) });
					}, function (xhr) {
						return lastFuncs.reject(new AjaxError(xhr.status, xhr.statusText));
					});
					return context$2$0.abrupt("return", [hasFiles, function () {
						return promises.shift();
					}]);

				case 51:
					context$2$0.prev = 51;
					context$2$0.next = 54;
					return $ajax(form.action, { method: "POST", data: formData });

				case 54:
					xhr = context$2$0.sent;
					return context$2$0.abrupt("return", $DOM(xhr.responseText));

				case 58:
					context$2$0.prev = 58;
					context$2$0.t27 = context$2$0["catch"](51);
					return context$2$0.abrupt("return", Promise.reject(new AjaxError(context$2$0.t27.status, context$2$0.t27.statusText)));

				case 61:
				case "end":
					return context$2$0.stop();
			}
		}, html5Submit, this, [[5, 33, 37, 45], [38,, 40, 44], [51, 58]]);
	});
	var initScript = regeneratorRuntime.mark(function initScript(checkDomains) {
		var formEl, str;
		return regeneratorRuntime.wrap(function initScript$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					new Logger().init();
					formEl = Initialization(checkDomains);

					if (formEl) {
						context$2$0.next = 4;
						break;
					}

					return context$2$0.abrupt("return");

				case 4:
					new Logger().log("Init");
					return context$2$0.delegateYield(getStored("DESU_Exclude"), "t30", 6);

				case 6:
					str = context$2$0.t30;

					if (!(str && str.includes(aib.dm))) {
						context$2$0.next = 9;
						break;
					}

					return context$2$0.abrupt("return");

				case 9:
					excludeList = str || "";

					if (Cfg) {
						context$2$0.next = 13;
						break;
					}

					return context$2$0.delegateYield(readCfg(), "t31", 12);

				case 12:
					new Logger().log("Config loading");

				case 13:
					if (Cfg.correctTime) {
						dTime = new DateTime(Cfg.timePattern, Cfg.timeRPattern, Cfg.timeOffset, lang, function (rp) {
							return saveCfg("timeRPattern", rp);
						});
						new Logger().log("Time correction");
					}

					if (!Cfg.disabled) {
						context$2$0.next = 18;
						break;
					}

					addPanel(formEl);
					scriptCSS();
					return context$2$0.abrupt("return");

				case 18:
					spells = new Spells(!!Cfg.hideBySpell);
					new Logger().log("Parsing spells");
					doc.body.style.display = "none";
					formEl = DelForm.doReplace(formEl);
					new Logger().log("Replace delform");
					pByNum = Object.create(null);
					context$2$0.prev = 24;

					dForm = new DelForm(formEl, false);
					context$2$0.next = 33;
					break;

				case 28:
					context$2$0.prev = 28;
					context$2$0.t32 = context$2$0["catch"](24);

					console.log("DELFORM ERROR:\n" + getPrettyErrorMessage(context$2$0.t32));
					doc.body.style.display = "";
					return context$2$0.abrupt("return");

				case 33:
					if (!localRun) {
						dForm.initAjax();
					}
					new Logger().log("Parse delform");
					pr = new PostForm($q(aib.qPostForm, doc), false, doc);
					new Logger().log("Parse postform");
					if (Cfg.hotKeys) {
						hKeys = new HotKeys();
						new Logger().log("Init keybinds");
					}
					initPage();
					new Logger().log("Init page");
					addPanel(formEl);
					new Logger().log("Add panel");
					initMessageFunctions();
					addDelformStuff();
					readViewedPosts();
					scriptCSS();
					new Logger().log("Apply CSS");
					doc.body.style.display = "";
					new Logger().log("Display page");
					if (needScroll) {
						scrollPage();
					}
					new Logger().log("Scroll page");
					readPosts();
					return context$2$0.delegateYield(readUserPosts(), "t33", 53);

				case 53:
					return context$2$0.delegateYield(readFavoritesPosts(), "t34", 54);

				case 54:
					setTimeout(PostContent.purge, 0);
					new Logger().log("Apply spells");
					new Logger().finish();

				case 57:
				case "end":
					return context$2$0.stop();
			}
		}, initScript, this, [[24, 28]]);
	});
	var version = "15.7.2.0",
	    defaultCfg = {
		disabled: 0,
		language: 0,
		hideBySpell: 1,
		spells: "",
		sortSpells: 0,
		menuHiddBtn: 1,
		hideRefPsts: 0,
		delHiddPost: 0,
		ajaxUpdThr: 1,
		updThrDelay: 20,
		noErrInTitle: 0,
		favIcoBlink: 0,
		markNewPosts: 1,
		desktNotif: 0,
		updCount: 1,
		expandTrunc: 0,
		postBtnsCSS: 2,
		showHideBtn: 1,
		showRepBtn: 1,
		noSpoilers: 1,
		noPostNames: 0,
		widePosts: 0,
		correctTime: 0,
		timeOffset: "+0",
		timePattern: "",
		timeRPattern: "",
		expandImgs: 2,
		imgNavBtns: 1,
		resizeDPI: 0,
		resizeImgs: 1,
		minImgSize: 100,
		zoomFactor: 25,
		webmControl: 1,
		webmVolume: 100,
		maskImgs: 0,
		preLoadImgs: 0,
		findImgFile: 0,
		openImgs: 0,
		imgSrcBtns: 1,
		delImgNames: 0,
		linksNavig: 2,
		linksOver: 100,
		linksOut: 1500,
		markViewed: 0,
		strikeHidd: 0,
		removeHidd: 0,
		noNavigHidd: 0,
		crossLinks: 0,
		insertNum: 1,
		addOPLink: 0,
		addImgs: 0,
		addMP3: 1,
		addVocaroo: 1,
		addYouTube: 3,
		YTubeType: 0,
		YTubeWidth: 360,
		YTubeHeigh: 270,
		YTubeTitles: 0,
		ytApiKey: "",
		addVimeo: 1,
		ajaxReply: 2,
		postSameImg: 1,
		removeEXIF: 1,
		removeFName: 0,
		sendErrNotif: 1,
		scrAfterRep: 0,
		addPostForm: 2,
		hangQReply: 1,
		spacedQuote: 1,
		favOnReply: 1,
		warnSubjTrip: 0,
		fileThumb: 1,
		addSageBtn: 1,
		saveSage: 1,
		sageReply: 0,
		captchaLang: 1,
		addTextBtns: 1,
		txtBtnsLoc: 1,
		passwValue: "",
		userName: 0,
		nameValue: "",
		noBoardRule: 1,
		noGoto: 1,
		noPassword: 1,
		noName: 0,
		scriptStyle: 0,
		userCSS: 0,
		userCSSTxt: "",
		expandPanel: 0,
		attachPanel: 1,
		panelCounter: 1,
		rePageTitle: 1,
		animation: 1,
		closePopups: 0,
		hotKeys: 1,
		loadPages: 1,
		updScript: 1,
		scrUpdIntrv: 1,
		turnOff: 0,
		textaWidth: 300,
		textaHeight: 115,
		qReplyX: "right: 0",
		qReplyY: "bottom: 25px"
	},
	    Lng = {
		cfg: {
			hideBySpell: ["Заклинания: ", "Magic spells: "],
			sortSpells: ["Сортировать спеллы и удалять дубликаты", "Sort spells and delete duplicates"],
			menuHiddBtn: ["Дополнительное меню кнопок скрытия ", "Additional menu of hide buttons"],
			hideRefPsts: ["Скрывать ответы на скрытые посты*", "Hide replies to hidden posts*"],
			delHiddPost: ["Удалять скрытые посты", "Delete hidden posts"],

			ajaxUpdThr: ["AJAX обновление треда ", "AJAX thread update "],
			updThrDelay: [" (сек)", " (sec)"],
			noErrInTitle: ["Не показывать номер ошибки в заголовке", "Don't show error number in title"],
			favIcoBlink: ["Мигать фавиконом при новых постах", "Favicon blinking on new posts"],
			markNewPosts: ["Выделять новые посты при смене вкладки", "Mark new posts when tab changes"],
			desktNotif: ["Уведомления на рабочем столе", "Desktop notifications"],
			updCount: ["Обратный счетчик секунд до обновления", "Show countdown to thread update"],
			expandTrunc: ["Разворачивать сокращенные посты*", "Auto expanding of truncated posts*"],
			postBtnsCSS: {
				sel: [["Text", "Classic", "Solid grey"], ["Text", "Classic", "Solid grey"]],
				txt: ["Кнопки постов* ", "Post buttons* "]
			},
			showHideBtn: ["Скрытие ", "Post hide "],
			showRepBtn: ["Ответ", "Post reply"],
			noSpoilers: ["Открывать текстовые спойлеры", "Open text spoilers"],
			noPostNames: ["Скрывать имена в постах", "Hide names in posts"],
			widePosts: ["Растягивать посты по ширине экрана", "Stretch posts to the screen width"],
			hotKeys: ["Горячие клавиши ", "Keyboard hotkeys "],
			loadPages: [" Количество страниц, загружаемых по F5", " Number of pages that are loaded on F5 "],
			correctTime: ["Коррекция времени в постах* ", "Correct time in posts* "],
			timeOffset: [" (ч) разница ", " (h) difference "],
			timePattern: [" Шаблон поиска", " Find pattern"],
			timeRPattern: [" Шаблон замены", " Replace pattern"],

			expandImgs: {
				sel: [["Откл.", "В посте", "По центру"], ["Disable", "In post", "By center"]],
				txt: ["Раскрывать картинки по клику", "Expand images on click"]
			},
			imgNavBtns: ["Добавлять кнопки навигации по картинкам", "Add buttons for images navigation"],
			resizeDPI: ["Отображать картинки пиксель в пиксель", "Don't upscale images on retina displays"],
			resizeImgs: ["Уменьшать в экран большие картинки", "Resize large images to fit screen"],
			minImgSize: [" Минимальный размер картинок (px)", " Minimal image's size (px)"],
			zoomFactor: [" Чувствительность зума картинок [1-100]", " Sensibility of the images zoom [1-100]"],
			webmControl: ["Показывать контрол-бар для webm-файлов", "Show control bar for webm files"],
			webmVolume: [" Громкость webm-файлов [0-100]", " Default volume for webm files [0-100]"],
			preLoadImgs: ["Предварительно загружать картинки*", "Pre-load images*"],
			findImgFile: ["Распознавать встроенные файлы в картинках*", "Detect built-in files in images*"],
			openImgs: {
				sel: [["Откл.", "Все подряд", "Только GIF", "Кроме GIF"], ["Disable", "All types", "Only GIF", "Non-GIF"]],
				txt: ["Скачивать полные версии картинок*", "Download full version of images*"]
			},
			imgSrcBtns: ["Добавлять кнопки для поиска картинок*", "Add image search buttons*"],
			delImgNames: ["Скрывать имена картинок*", "Hide names of images*"],

			linksNavig: {
				sel: [["Откл.", "Без карты", "С картой"], ["Disable", "No map", "With map"]],
				txt: ["Навигация по >>ссылкам* ", "Navigation by >>links* "]
			},
			linksOver: [" Появление ", " Appearance "],
			linksOut: [" Пропадание (мс)", " Disappearance (ms)"],
			markViewed: ["Отмечать просмотренные посты*", "Mark viewed posts*"],
			strikeHidd: ["Зачеркивать >>ссылки на скрытые посты", "Strike >>links to hidden posts"],
			removeHidd: ["Удалять из карты ответов", "Remove from replies map"],
			noNavigHidd: ["Не отображать превью для скрытых постов", "Don't show previews for hidden posts"],
			crossLinks: ["Преобразовывать http:// в >>/b/ссылки*", "Replace http:// with >>/b/links*"],
			insertNum: ["Вставлять >>ссылку по клику на №поста*", "Insert >>link on №postnumber click*"],
			addOPLink: ["Вставлять >>ссылку при ответе на оп-пост на доске", "Insert >>link for reply to op-posts on board"],
			addMP3: ["Плеер к mp3 ссылкам* ", "Player to mp3 links* "],
			addImgs: ["Загружать картинки к jpg, png, gif ссылкам*", "Load images to jpg, png, gif links*"],
			addVocaroo: ["Плеер к Vocaroo ссылкам*", "Player to Vocaroo links*"],
			addVimeo: ["Добавлять плеер к Vimeo ссылкам*", "Add player to Vimeo links*"],
			addYouTube: {
				sel: [["Ничего", "Плеер по клику", "Авто плеер", "Превью+плеер", "Только превью"], ["Nothing", "On click player", "Auto player", "Preview+player", "Only preview"]],
				txt: ["к YouTube-ссылкам* ", "to YouTube-links* "]
			},
			YTubeType: {
				sel: [["Flash", "HTML5"], ["Flash", "HTML5"]],
				txt: ["", ""]
			},
			YTubeTitles: ["Загружать названия к YouTube-ссылкам*", "Load titles into YouTube-links*"],
			ytApiKey: ["Ключ YouTube API*", "YouTube API Key*"],

			ajaxReply: {
				sel: [["Откл.", "Iframe", "HTML5"], ["Disable", "Iframe", "HTML5"]],
				txt: ["AJAX отправка постов*", "posting with AJAX*"]
			},
			postSameImg: ["Возможность отправки одинаковых картинок", "Ability to post same images"],
			removeEXIF: ["Удалять EXIF из JPEG ", "Remove EXIF from JPEG "],
			removeFName: ["Удалять имя файлов", "Clear file names"],
			sendErrNotif: ["Оповещать в заголовке об ошибке отправки", "Inform in title about post send error"],
			scrAfterRep: ["Перемещаться в конец треда после отправки", "Scroll to the bottom after reply"],
			addPostForm: {
				sel: [["Сверху", "Внизу", "Скрытая"], ["At top", "At bottom", "Hidden"]],
				txt: ["Форма ответа в треде", "Reply form in thread"]
			},
			spacedQuote: ["Вставлять пробел при цитировании \"> \"", "Insert a space when quoting \"> \""],
			favOnReply: ["Добавлять тред в избранное при ответе", "Add thread to favorites on reply"],
			warnSubjTrip: ["Оповещать при наличии трип-кода в поле \"Тема\"", "Warn if \"Subject\" field contains trip-code"],
			fileThumb: ["Область превью картинок вместо кнопки \"Файл\"", "File thumbnail area instead of \"File\" button"],
			addSageBtn: ["Кнопка Sage вместо \"E-mail\"* ", "Sage button instead of \"E-mail\"* "],
			saveSage: ["Запоминать сажу", "Remember sage"],
			captchaLang: {
				sel: [["Откл.", "Eng", "Rus"], ["Disable", "Eng", "Rus"]],
				txt: ["Язык ввода капчи", "Language input in captcha"]
			},
			addTextBtns: {
				sel: [["Откл.", "Графич.", "Упрощ.", "Стандарт."], ["Disable", "As images", "As text", "Standard"]],
				txt: ["Кнопки разметки текста ", "Text format buttons "]
			},
			txtBtnsLoc: ["Внизу", "At bottom"],
			userPassw: [" Постоянный пароль ", " Fixed password "],
			userName: ["Постоянное имя", "Fixed name"],
			noBoardRule: ["правила ", "rules "],
			noGoto: ["поле goto ", "goto field "],
			noPassword: ["пароль ", "password "],
			noName: ["имя", "name"],

			scriptStyle: {
				sel: [["Glass black", "Glass blue", "Solid grey"], ["Glass black", "Glass blue", "Solid grey"]],
				txt: ["Стиль скрипта", "Script style"]
			},
			userCSS: ["Пользовательский CSS ", "User CSS "],
			attachPanel: ["Прикрепить главную панель", "Attach main panel"],
			panelCounter: ["Счетчик постов/картинок на главной панели", "Counter of posts/images on main panel"],
			rePageTitle: ["Название треда в заголовке вкладки*", "Thread title in page tab*"],
			animation: ["CSS3 анимация в скрипте", "CSS3 animation in script"],
			closePopups: ["Автоматически закрывать уведомления", "Close popups automatically"],
			updScript: ["Автоматически проверять обновления скрипта", "Check for script update automatically"],
			scrUpdIntrv: {
				sel: [["Каждый день", "Каждые 2 дня", "Каждую неделю", "Каждые 2 недели", "Каждый месяц"], ["Every day", "Every 2 days", "Every week", "Every 2 week", "Every month"]],
				txt: ["", ""]
			},
			excludeList: ["Список доменов, запрещающих запуск скрипта:", "Domains list for preventing script launch:"],
			turnOff: ["Включать скрипт только на этом сайте", "Enable script only on this site"],

			language: {
				sel: [["Ru", "En"], ["Ru", "En"]],
				txt: ["", ""]
			}
		},

		txtBtn: [["Жирный", "Bold"], ["Наклонный", "Italic"], ["Подчеркнутый", "Underlined"], ["Зачеркнутый", "Strike"], ["Спойлер", "Spoiler"], ["Код", "Code"], ["Верхний индекс", "Superscript"], ["Нижний индекс", "Subscript"], ["Цитировать выделенное", "Quote selected"]],

		cfgTab: {
			filters: ["Фильтры", "Filters"],
			posts: ["Посты", "Posts"],
			images: ["Картинки", "Images"],
			links: ["Ссылки", "Links"],
			form: ["Форма", "Form"],
			common: ["Общее", "Common"],
			info: ["Инфо", "Info"]
		},

		panelBtn: {
			attach: ["Прикрепить/Открепить", "Attach/Detach"],
			settings: ["Настройки", "Settings"],
			hidden: ["Скрытое", "Hidden"],
			favor: ["Избранное", "Favorites"],
			video: ["Видео-ссылки", "Video links"],
			refresh: ["Обновить", "Refresh"],
			goback: ["Назад", "Go back"],
			gonext: ["Следующая", "Next"],
			goup: ["Наверх", "To the top"],
			godown: ["В конец", "To the bottom"],
			expimg: ["Раскрыть картинки", "Expand images"],
			preimg: ["Предзагрузка картинок ([Ctrl+Click] только для новых постов)", "Preload images ([Ctrl+Click] for new posts only)"],
			maskimg: ["Маскировать картинки", "Mask images"],
			"upd-on": ["Выключить автообновление треда", "Disable thread autoupdate"],
			"upd-off": ["Включить автообновление треда", "Enable thread autoupdate"],
			"audio-off": ["Звуковое оповещение о новых постах", "Sound notification about new posts"],
			catalog: ["Каталог", "Catalog"],
			counter: ["Постов/картинок в треде", "Posts/Images in thread"],
			savethr: ["Сохранить на диск", "Save to disk"],
			enable: ["Включить/выключить скрипт", "Turn on/off the script"]
		},

		selHiderMenu: {
			sel: ["Скрывать выделенное", "Hide selected text"],
			name: ["Скрывать имя", "Hide name"],
			trip: ["Скрывать трип-код", "Hide with trip-code"],
			img: ["Скрывать картинку", "Hide with image"],
			ihash: ["Скрывать схожие картинки", "Hide similar images"],
			text: ["Скрыть схожий текст", "Hide similar text"],
			noimg: ["Скрывать без картинок", "Hide without images"],
			notext: ["Скрывать без текста", "Hide without text"]
		},
		selExpandThr: [["5 постов", "15 постов", "30 постов", "50 постов", "100 постов"], ["5 posts", "15 posts", "30 posts", "50 posts", "100 posts"]],
		selAjaxPages: [["1 страница", "2 страницы", "3 страницы", "4 страницы", "5 страниц"], ["1 page", "2 pages", "3 pages", "4 pages", "5 pages"]],
		selSaveThr: [["Скачать весь тред", "Скачать картинки"], ["Download thread", "Download images"]],
		selAudioNotif: [["Каждые 30 сек.", "Каждую минуту", "Каждые 2 мин.", "Каждые 5 мин."], ["Every 30 sec.", "Every minute", "Every 2 min.", "Every 5 min."]],

		hotKeyEdit: [["%l%i24 – предыдущая страница/картинка%/l", "%l%i217 – следующая страница/картинка%/l", "%l%i21 – тред (на доске)/пост (в треде) ниже%/l", "%l%i20 – тред (на доске)/пост (в треде) выше%/l", "%l%i31 – пост (на доске) ниже%/l", "%l%i30 – пост (на доске) выше%/l", "%l%i23 – скрыть пост/тред%/l", "%l%i32 – перейти в тред%/l", "%l%i33 – развернуть тред%/l", "%l%i211 – раскрыть картинку в посте%/l", "%l%i22 – быстрый ответ%/l", "%l%i25t – отправить пост%/l", "%l%i210 – открыть/закрыть \"Настройки\"%/l", "%l%i26 – открыть/закрыть \"Избранное\"%/l", "%l%i27 – открыть/закрыть \"Скрытое\"%/l", "%l%i218 – открыть/закрыть \"Видео\"%/l", "%l%i28 – открыть/закрыть панель%/l", "%l%i29 – включить/выключить маскировку картинок%/l", "%l%i40 – обновить тред (в треде)%/l", "%l%i212t – жирный%/l", "%l%i213t – курсив%/l", "%l%i214t – зачеркнутый%/l", "%l%i215t – спойлер%/l", "%l%i216t – код%/l"], ["%l%i24 – previous page/image%/l", "%l%i217 – next page/image%/l", "%l%i21 – thread (on board)/post (in thread) below%/l", "%l%i20 – thread (on board)/post (in thread) above%/l", "%l%i31 – on board post below%/l", "%l%i30 – on board post above%/l", "%l%i23 – hide post/thread%/l", "%l%i32 – go to thread%/l", "%l%i33 – expand thread%/l", "%l%i211 – expand post's images%/l", "%l%i22 – quick reply%/l", "%l%i25t – send post%/l", "%l%i210 – open/close \"Settings\"%/l", "%l%i26 – open/close \"Favorites\"%/l", "%l%i27 – open/close \"Hidden\"%/l", "%l%i218 – open/close \"Videos\"%/l", "%l%i28 – open/close the main panel%/l", "%l%i29 – turn on/off masking images%/l", "%l%i40 – update thread%/l", "%l%i212t – bold%/l", "%l%i213t – italic%/l", "%l%i214t – strike%/l", "%l%i215t – spoiler%/l", "%l%i216t – code%/l"]],

		month: [["янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"], ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]],
		fullMonth: [["января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"], ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]],
		week: [["Вск", "Пнд", "Втр", "Срд", "Чтв", "Птн", "Сбт"], ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]],

		editor: {
			cfg: ["Редактирование настроек:", "Edit settings:"],
			hidden: ["Редактирование скрытых тредов:", "Edit hidden threads:"],
			favor: ["Редактирование избранного:", "Edit favorites:"],
			css: ["Редактирование CSS", "Edit CSS"]
		},

		newPost: [[" новый пост", " новых поста", " новых постов", ". Последний:"], [" new post", " new posts", " new posts", ". Latest: "]],

		name: ["Имя", "Name"],
		subj: ["Тема", "Subject"],
		mail: ["E-mail", "E-mail"],
		cap: ["Капча", "Captcha"],
		video: ["Видео", "Video"],
		add: ["Добавить", "Add"],
		apply: ["Применить", "Apply"],
		clear: ["Очистить", "Clear"],
		refresh: ["Обновить", "Refresh"],
		load: ["Загрузить", "Load"],
		save: ["Сохранить", "Save"],
		edit: ["Правка", "Edit"],
		reset: ["Сброс", "Reset"],
		remove: ["Удалить", "Remove"],
		info: ["Инфо", "Info"],
		undo: ["Отмена", "Undo"],
		change: ["Сменить", "Change"],
		reply: ["Ответ", "Reply"],
		loading: ["Загрузка...", "Loading..."],
		checking: ["Проверка...", "Checking..."],
		deleting: ["Удаление...", "Deleting..."],
		error: ["Ошибка", "Error"],
		noConnect: ["Ошибка подключения", "Connection failed"],
		internalError: ["Ошибка скрипта:\n", "Script error:\n"],
		thrNotFound: ["Тред недоступен (№", "Thread is unavailable (№"],
		succDeleted: ["Успешно удалено!", "Succesfully deleted!"],
		errDelete: ["Не могу удалить:\n", "Can't delete:\n"],
		cTimeError: ["Неправильные настройки времени", "Invalid time settings"],
		noGlobalCfg: ["Глобальные настройки не найдены", "Global config not found"],
		postNotFound: ["Пост не найден", "Post not found"],
		dontShow: ["Скрыть: ", "Hide: "],
		checkNow: ["Проверить сейчас", "Check now"],
		updAvail: ["Доступно обновление!", "Update available!"],
		haveLatest: ["У вас стоит самая последняя версия!", "You have latest version!"],
		storage: ["Хранение: ", "Storage: "],
		thrViewed: ["Тредов посещено", "Threads visited"],
		thrCreated: ["Тредов создано", "Threads created"],
		thrHidden: ["Тредов скрыто", "Threads hidden"],
		postsSent: ["Постов отправлено", "Posts sent"],
		total: ["Всего", "Total"],
		debug: ["Отладка", "Debug"],
		infoDebug: ["Информация для отладки", "Information for debugging"],
		loadGlobal: ["Загрузить глобальные настройки", "Load global settings"],
		saveGlobal: ["Сохранить настройки как глобальные", "Save settings as global"],
		editInTxt: ["Правка в текстовом формате", "Edit in text format"],
		resetCfg: ["Сбросить в настройки по умолчанию", "Reset settings to defaults"],
		conReset: ["Данное действие удалит все ваши настройки и закладки. Продолжить?", "This will delete all your preferences and favourites. Continue?"],
		clrSelected: ["Удалить выделенные записи", "Remove selected notes"],
		saveChanges: ["Сохранить внесенные изменения", "Save your changes"],
		infoCount: ["Обновить счетчики постов", "Refresh posts counters"],
		infoPage: ["Проверить актуальность тредов (до 5 страницы)", "Check for threads actuality (up to 5 page)"],
		clrDeleted: ["Очистить недоступные (404) треды", "Clear inaccessible (404) threads"],
		oldPosts: ["Постов при последнем посещении", "Posts at the last visit"],
		newPosts: ["Количество новых постов", "Number of new posts"],
		thrPage: ["Тред на @странице", "Thread on @page"],
		hiddenPosts: ["Скрытые посты на странице", "Hidden posts on the page"],
		hiddenThrds: ["Скрытые треды", "Hidden threads"],
		noHidPosts: ["На этой странице нет скрытых постов...", "No hidden posts on this page..."],
		noHidThrds: ["Нет скрытых тредов...", "No hidden threads..."],
		expandAll: ["Раскрыть все", "Expand all"],
		invalidData: ["Некорректный формат данных", "Incorrect data format"],
		favThrds: ["Избранные треды:", "Favorite threads:"],
		noFavThrds: ["Нет избранных тредов...", "Favorites is empty..."],
		noVideoLinks: ["Нет ссылок на видео...", "No video links..."],
		hideLnkList: ["Скрыть/Показать список ссылок", "Hide/Unhide list of links"],
		prevVideo: ["Предыдущее видео", "Previous video"],
		nextVideo: ["Следующее видео", "Next video"],
		toggleQReply: ["Поместить под пост / Открепить", "Move under post / Unattach"],
		closeQReply: ["Закрыть форму", "Close form"],
		replies: ["Ответы:", "Replies:"],
		postsOmitted: ["Пропущено ответов: ", "Posts omitted: "],
		collapseThrd: ["Свернуть тред", "Collapse thread"],
		deleted: ["удалён", "deleted"],
		getNewPosts: ["Получить новые посты", "Get new posts"],
		page: ["Страница", "Page"],
		hiddenThrd: ["Скрытый тред:", "Hidden thread:"],
		makeThrd: ["Создать тред", "Create thread"],
		makeReply: ["Ответить", "Make reply"],
		noSage: ["Без сажи", "No sage"],
		hideForm: ["Скрыть форму", "Hide form"],
		search: ["Искать в ", "Search in "],
		wait: ["Ждите", "Wait"],
		noFile: ["Нет файла", "No file"],
		clickToAdd: ["Выберите, либо перетащите файл", "Select or drag and drop file"],
		removeFile: ["Удалить файл", "Remove file"],
		spoilFile: ["Спойлер", "Spoiler"],
		helpAddFile: ["Встроить .ogg, .rar, .zip или .7z в картинку", "Pack .ogg, .rar, .zip or .7z into image"],
		downloadFile: ["Скачать содержащийся в картинке файл", "Download existing file from image"],
		fileCorrupt: ["Файл повреждён: ", "File is corrupted: "],
		subjHasTrip: ["Поле \"Тема\" содержит трипкод", "\"Subject\" field contains a tripcode"],
		loadImage: ["Загружаются картинки: ", "Loading images: "],
		loadFile: ["Загружаются файлы: ", "Loading files: "],
		cantLoad: ["Не могу загрузить ", "Can't load "],
		willSavePview: ["Будет сохранено превью", "Thumbnail will be saved"],
		loadErrors: ["Во время загрузки произошли ошибки:", "An error occurred during the loading:"],
		errCorruptData: ["Ошибка: сервер отправил повреждённые данные", "Error: server sent corrupted data"],
		expImgInline: ["[Click] открыть в посте, [Ctrl+Click] в центре", "[Click] expand in post, [Ctrl+Click] by center"],
		expImgFull: ["[Click] открыть в центре, [Ctrl+Click] в посте", "[Click] expand by center, [Ctrl+Click] in post"],
		nextImg: ["Следующая картинка", "Next image"],
		prevImg: ["Предыдущая картинка", "Previous image"],
		togglePost: ["Скрыть/Раскрыть пост", "Hide/Unhide post"],
		replyToPost: ["Ответить на пост", "Reply to post"],
		expandThrd: ["Раскрыть весь тред", "Expand all thread"],
		addFav: ["Добавить тред в Избранное", "Add thread to Favorites"],
		delFav: ["Убрать тред из Избранного", "Remove thread from Favorites"],
		attachPview: ["Закрепить превью", "Attach preview"],
		expandVideo: ["Развернуть/Свернуть видео", "Expand/Collapse video"],
		author: ["автор: ", "author: "],
		views: ["просмотров: ", "views: "],
		published: ["опубликовано: ", "published: "],

		seSyntaxErr: ["синтаксическая ошибка в аргументе спелла: %s", "syntax error in argument of spell: %s"],
		seUnknown: ["неизвестный спелл: %s", "unknown spell: %s"],
		seMissOp: ["пропущен оператор", "missing operator"],
		seMissArg: ["пропущен аргумент спелла: %s", "missing argument of spell: %s"],
		seMissSpell: ["пропущен спелл", "missing spell"],
		seErrRegex: ["синтаксическая ошибка в регулярном выражении: %s", "syntax error in regular expression: %s"],
		seUnexpChar: ["неожиданный символ: %s", "unexpected character: %s"],
		seMissClBkt: ["пропущена закрывающаяся скобка", "missing ) in parenthetical"],
		seRepsInParens: ["спелл $s не должен располагаться в скобках", "spell %s shouldn't be in parens"],
		seOpInReps: ["недопустимо использовать оператор %s со спеллами #rep и #outrep", "don't use operator %s with spells #rep & #outrep"],
		seRow: [" (строка ", " (row "],
		seCol: [", столбец ", ", column "],
		sendingPost: ["Отправка поста...", "Sending post..."],
		sizeByte: [" Байт", " Byte"],
		sizeKByte: [" КБ", " KB"],
		sizeMByte: [" МБ", " MB"],
		sizeGByte: [" ГБ", " GB"],
		second: ["с", "s"]
	},
	    doc = window.document,
	    aProto = Array.prototype,
	    locStorage,
	    sesStorage,
	    Cfg,
	    hThr,
	    pByNum,
	    sVis,
	    uVis,
	    needScroll,
	    aib,
	    nav,
	    updater,
	    hKeys,
	    visPosts = 2,
	    dTime,
	    WebmParser,
	    Logger,
	    pr,
	    dForm,
	    dummy,
	    spells,
	    Images_ = { preloading: false, afterpreload: null, progressId: null, canvas: null },
	    lang,
	    quotetxt = "",
	    localRun,
	    isExpImg,
	    isPreImg,
	    chromeCssUpd,
	    excludeList,
	    $each = Function.prototype.call.bind(aProto.forEach),
	    emptyFn = Function.prototype;




	function $Q(path, root) {
		return root.querySelectorAll(path);
	}

	function $q(path, root) {
		return root.querySelector(path);
	}

	function $C(id, root) {
		return root.getElementsByClassName(id);
	}

	function $c(id, root) {
		return root.getElementsByClassName(id)[0];
	}

	function $id(id) {
		return doc.getElementById(id);
	}

	function $T(id, root) {
		return root.getElementsByTagName(id);
	}

	function $t(id, root) {
		return root.getElementsByTagName(id)[0];
	}

	function $parent(el, tagName) {
		do {
			el = el.parentElement;
		} while (el && el.tagName !== tagName);
		return el;
	}

	function $before(el, node) {
		el.parentNode.insertBefore(node, el);
	}

	function $after(el, node) {
		el.parentNode.insertBefore(node, el.nextSibling);
	}

	function $add(html) {
		dummy.innerHTML = html;
		return dummy.firstChild;
	}

	function $new(tag, attr, events) {
		var el = doc.createElement(tag);
		if (attr) {
			for (var key in attr) {
				if (key === "text") {
					el.textContent = attr[key];
				} else if (key === "value") {
					el.value = attr[key];
				} else if (attr.hasOwnProperty(key)) {
					el.setAttribute(key, attr[key]);
				}
			}
		}
		if (events) {
			for (var key in events) {
				if (events.hasOwnProperty(key)) {
					el.addEventListener(key, events[key], false);
				}
			}
		}
		return el;
	}

	function $New(tag, attr, nodes) {
		for (var i = 0, len = nodes.length, el = $new(tag, attr, null); i < len; i++) {
			if (nodes[i]) {
				el.appendChild(nodes[i]);
			}
		}
		return el;
	}

	function $txt(el) {
		return doc.createTextNode(el);
	}

	function $btn(val, ttl, Fn) {
		return $new("input", { type: "button", value: val, title: ttl }, { click: Fn });
	}

	function $script(text) {
		$del(doc.head.appendChild($new("script", { type: "text/javascript", text: text }, null)));
	}

	function $css(text) {
		return doc.head.appendChild($new("style", { type: "text/css", text: text }, null));
	}

	function $if(cond, el) {
		return cond ? el : null;
	}

	function $disp(el) {
		el.style.display = el.style.display === "none" ? "" : "none";
	}

	function $del(el) {
		if (el) {
			el.parentNode.removeChild(el);
		}
	}

	function $DOM(html) {
		var myDoc = doc.implementation.createHTMLDocument("");
		myDoc.documentElement.innerHTML = html;
		return myDoc;
	}

	function $pd(e) {
		e.preventDefault();
	}

	function $txtInsert(el, txt) {
		var scrtop = el.scrollTop,
		    start = el.selectionStart;
		el.value = el.value.substr(0, start) + txt + el.value.substr(el.selectionEnd);
		el.setSelectionRange(start + txt.length, start + txt.length);
		el.focus();
		el.scrollTop = scrtop;
	}

	function $txtSelect() {
		return (nav.Presto ? doc.getSelection() : window.getSelection()).toString();
	}

	function $isEmpty(obj) {
		for (var i in obj) {
			if (obj.hasOwnProperty(i)) {
				return false;
			}
		}
		return true;
	}

	Logger = new function () {
		var instance,
		    marks = [],
		    finished = false;
		function LoggerSingleton() {
			if (instance) {
				return instance;
			}
			instance = this;
		}
		LoggerSingleton.prototype = {
			finish: function finish() {
				finished = true;
				marks.push(["LoggerFinish", Date.now()]);
			},
			getData: function getData(full) {
				var duration,
				    timeLog = [],
				    i = 1;
				for (var len = marks.length - 1, lastExtra = 0; i < len; ++i) {
					duration = marks[i][1] - marks[i - 1][1] + lastExtra;
					if (full || duration > 1) {
						lastExtra = 0;
						timeLog.push([marks[i][0], duration]);
					} else {
						lastExtra = duration;
					}
				}
				duration = marks[i][1] - marks[0][1];
				timeLog.push([Lng.total[lang], duration]);
				return timeLog;
			},
			getTable: function getTable() {
				var html = "<tbody>",
				    data = this.getData(false);
				for (var i = 0, len = data.length; i < len; ++i) {
					html += "<tr><td>" + data[i][0] + "</td><td>" + data[i][1] + "ms</td></tr>";
				}
				return html + "</tbody>";
			},
			init: function init() {
				marks.push(["LoggerInit", Date.now()]);
			},
			log: function log(text) {
				if (!finished) {
					marks.push([text, Date.now()]);
				}
			}
		};
		return LoggerSingleton;
	}();

	function async(generatorFunc) {
		return function () {
			for (var _len = arguments.length, args = Array(_len), _key = 0; _key < _len; _key++) {
				args[_key] = arguments[_key];
			}

			function continuer(verb, arg) {
				var result;
				try {
					result = generator[verb](arg);
				} catch (err) {
					console.log("Generator throw: ", err);
					return Promise.reject(err);
				}
				return result.done ? result.value : Promise.resolve(result.value).then(onFulfilled, onRejected);
			}
			var generator = generatorFunc.apply(this, args),
			    onFulfilled = continuer.bind(continuer, "next"),
			    onRejected = continuer.bind(continuer, "throw");
			return onFulfilled();
		};
	}

	function spawn(generatorFunc) {
		for (var _len = arguments.length, args = Array(_len > 1 ? _len - 1 : 0), _key = 1; _key < _len; _key++) {
			args[_key - 1] = arguments[_key];
		}

		return Promise.resolve(async(generatorFunc).apply(undefined, args));
	}

	function sleep(ms) {
		return new Promise(function (resolve, reject) {
			return setTimeout(function () {
				return resolve();
			}, ms);
		});
	}

	function $ajax(url) {
		var params = arguments[1] === undefined ? null : arguments[1];
		var useNative = arguments[2] === undefined ? true : arguments[2];

		return new Promise(function (resolve, reject) {
			if (!useNative && typeof GM_xmlhttpRequest === "function") {
				var obj = {
					method: params && params.method || "GET",
					url: nav.fixLink(url),
					onload: function (e) {
						if (e.status === 200) {
							resolve(e);
						} else {
							reject(e);
						}
					}
				};
				if (params) {
					delete params.method;
					Object.assign(obj, params);
				}
				GM_xmlhttpRequest(obj);
				return;
			}
			var useCache = params && params.useCache;
			var xhr = new XMLHttpRequest();
			if (params && params.onprogress) {
				xhr.upload.onprogress = params.onprogress;
			}
			xhr.onreadystatechange = function (_ref) {
				var target = _ref.target;

				if (target.readyState === 4) {
					if (target.status === 200) {
						if (useCache) {
							aib.LastModified = target.getResponseHeader("Last-Modified");
							aib.ETag = xhr.getResponseHeader("Etag");
						}
						resolve(target);
					} else {
						reject(target);
					}
				}
			};
			xhr.open(params && params.method || "GET", url, true);
			if (params) {
				if (params.responseType) {
					xhr.responseType = params.responseType;
				}
				var headers = params.headers;
				if (headers) {
					for (var h in headers) {
						if (headers.hasOwnProperty(h)) {
							xhr.setRequestHeader(h, headers[h]);
						}
					}
				}
			}
			if (useCache) {
				if (aib.LastModified) {
					xhr.setRequestHeader("If-Modified-Since", aib.LastModified);
				}
				if (aib.ETag) {
					xhr.setRequestHeader("If-None-Match", aib.ETag);
				}
			}
			xhr.send(params && params.data || null);
		});
	}

	function TasksPool(tasksCount, taskFunc, endFn) {
		this.array = [];
		this.running = 0;
		this.num = 1;
		this.func = taskFunc;
		this.endFn = endFn;
		this.max = tasksCount;
		this.completed = this.paused = this.stopped = false;
	}
	TasksPool.PauseError = function (duration) {
		this.name = "TasksPool.PauseError";
		this.duration = duration;
	};
	TasksPool.prototype = {
		complete: function complete() {
			if (!this.stopped) {
				if (this.array.length === 0 && this.running === 0) {
					this.endFn();
				} else {
					this.completed = true;
				}
			}
		},
		"continue": function () {
			if (!this.stopped) {
				this.paused = false;
				if (this.array.length === 0) {
					if (this.completed) {
						this.endFn();
					}
					return;
				}
				while (this.array.length !== 0 && this.running !== this.max) {
					this._run(this.array.shift());
					this.running++;
				}
			}
		},
		pause: function pause() {
			this.paused = true;
		},
		run: function run(data) {
			if (!this.stopped) {
				if (this.paused || this.running === this.max) {
					this.array.push(data);
				} else {
					this._run(data);
					this.running++;
				}
			}
		},
		stop: function stop() {
			this.stopped = true;
			this.endFn();
		},

		_end: function _end() {
			if (!this.stopped) {
				if (!this.paused && this.array.length !== 0) {
					this._run(this.array.shift());
					return;
				}
				this.running--;
				if (!this.paused && this.completed && this.running === 0) {
					this.endFn();
				}
			}
		},
		_run: function _run(data) {
			var _this = this;

			this.func(this.num++, data).then(function () {
				return _this._end();
			}, function (e) {
				if (e instanceof TasksPool.PauseError) {
					_this.pause();
					if (e.duration !== -1) {
						setTimeout(function () {
							return _this["continue"]();
						}, e.duration);
					}
				} else {
					_this._end();
					throw e;
				}
			});
		}
	};

	function TarBuilder() {
		this._data = [];
	}
	TarBuilder.prototype = {
		addFile: function addFile(filepath, input) {
			var i = 0,
			    checksum = 0,
			    fileSize = input.length,
			    header = new Uint8Array(512);
			for (var nameLen = Math.min(filepath.length, 100); i < nameLen; ++i) {
				header[i] = filepath.charCodeAt(i) & 255;
			}
		
			this._padSet(header, 100, "100777", 8);
		
			this._padSet(header, 108, "0", 8);
		
			this._padSet(header, 116, "0", 8);
		
			this._padSet(header, 124, fileSize.toString(8), 13);
		
			this._padSet(header, 136, Math.floor(Date.now() / 1000).toString(8), 12);
		
			this._padSet(header, 148, "        ", 8);
		
			header[156] = 48;
			for (i = 0; i < 157; i++) {
				checksum += header[i];
			}
		
			this._padSet(header, 148, checksum.toString(8), 8);
			this._data.push(header);
			this._data.push(input);
			if ((i = Math.ceil(fileSize / 512) * 512 - fileSize) !== 0) {
				this._data.push(new Uint8Array(i));
			}
		},
		addString: function addString(filepath, str) {
			var i,
			    len,
			    data,
			    sDat = unescape(encodeURIComponent(str));
			for (i = 0, len = sDat.length, data = new Uint8Array(len); i < len; ++i) {
				data[i] = sDat.charCodeAt(i) & 255;
			}
			this.addFile(filepath, data);
		},
		get: function get() {
			this._data.push(new Uint8Array(1024));
			return new Blob(this._data, { type: "application/x-tar" });
		},

		_padSet: function _padSet(data, offset, num, len) {
			var i = 0,
			    nLen = num.length;
			len -= 2;
			while (nLen < len) {
				data[offset++] = 32;
				len--;
			}
			while (i < nLen) {
				data[offset++] = num.charCodeAt(i++);
			}
			data[offset] = 32;
		}
	};

	function regQuote(str) {
		return (str + "").replace(/([.?*+^$[\]\\(){}|\-])/g, "\\$1");
	}

	function fixBrd(b) {
		return "/" + b + (b ? "/" : "");
	}

	function getAbsLink(url) {
		return url[1] === "/" ? aib.prot + url : url[0] === "/" ? aib.prot + "//" + aib.host + url : url;
	}

	function getErrorMessage(e) {
		if (e instanceof AjaxError) {
			return e.code === 0 ? e.message || Lng.noConnect[lang] : "HTTP [" + e.code + "] " + e.message;
		}
		return typeof e === "string" ? e : Lng.internalError[lang] + getPrettyErrorMessage(e);
	}

	function getPrettyErrorMessage(e) {
		return e.stack ? nav.WebKit ? e.stack : e.name + ": " + e.message + "\n" + (nav.Firefox ? e.stack.replace(/^([^@]*).*\/(.+)$/gm, function (str, fName, line) {
			return "    at " + (fName ? fName + " (" + line + ")" : line);
		}) : e.stack) : e.name + ": " + e.message;
	}

	function toRegExp(str, noG) {
		var l = str.lastIndexOf("/"),
		    flags = str.substr(l + 1);
		return new RegExp(str.substr(1, l - 1), noG ? flags.replace("g", "") : flags);
	}

	function setImageSize(size, idx, nVal) {
		size[idx] = nVal;
		if (idx === 0) {
			size[1] = nVal / size[2];
		} else {
			size[0] = nVal * size[2];
		}
	}

	function resizeImage(size, minSize, maxSize) {
		var idx = size[2] > 1 ? 1 : 0;
		if (+size[idx] < +minSize) {
			setImageSize(size, idx, minSize);
		}
		if (maxSize) {
			idx = size[2] > maxSize[2] ? 0 : 1;
			if (+size[idx] > +maxSize[idx]) {
				setImageSize(size, idx, +maxSize[idx]);
			}
		}
		return size;
	}

	function isFormElDisabled(el) {
	
		switch (el.tagName.toLowerCase()) {
			case "button":
			case "input":
			case "select":
			case "textarea":
				if (el.hasAttribute("disabled")) {
					return true;
				}
						default:
				if (nav.matchesSelector(el, "fieldset[disabled] > :not(legend):not(:first-of-type) *")) {
					return true;
				}
		}
		return false;
	}

	function prettifySize(val) {
		if (val > 512 * 1024 * 1024) {
			return (val / (1024 * 1024 * 1024)).toFixed(2) + Lng.sizeGByte[lang];
		}
		if (val > 512 * 1024) {
			return (val / (1024 * 1024)).toFixed(2) + Lng.sizeMByte[lang];
		}
		if (val > 512) {
			return (val / 1024).toFixed(2) + Lng.sizeKByte[lang];
		}
		return val.toFixed(2) + Lng.sizeByte[lang];
	}

	function setStored(id, value) {
		if (nav.isGM) {
			GM_setValue(id, value);
		} else if (nav.isChromeStorage) {
			var obj = {};
			obj[id] = value;
			if (value.toString().length < 4095) {
				chrome.storage.sync.set(obj, emptyFn);
				chrome.storage.local.remove(id, emptyFn);
			} else {
				chrome.storage.local.set(obj, emptyFn);
				chrome.storage.sync.remove(id, emptyFn);
			}
		} else if (nav.isScriptStorage) {
			scriptStorage.setItem(id, value);
		} else {
			locStorage[id] = value;
		}
	}

	function delStored(id) {
		if (nav.isGM) {
			GM_deleteValue(id);
		} else if (nav.isChromeStorage) {
			chrome.storage.sync.remove(id, emptyFn);
		} else if (nav.isScriptStorage) {
			scriptStorage.removeItem(id);
		} else {
			locStorage.removeItem(id);
		}
	}

	function saveComCfg(dm, obj) {
		spawn(getStoredObj, "DESU_Config").then(function (val) {
			if (obj) {
				val[dm] = obj;
			} else {
				delete val[dm];
			}
			setStored("DESU_Config", JSON.stringify(val));
		});
	}

	function saveCfg(id, val) {
		if (Cfg[id] !== val) {
			Cfg[id] = val;
			saveComCfg(aib.dm, Cfg);
		}
	}

	function toggleCfg(id) {
		saveCfg(id, +!Cfg[id]);
	}

	function readFav() {
		return spawn(getStoredObj, "DESU_Favorites");
	}

	function readPosts() {
		var str = aib.t ? sesStorage["de-hidden-" + aib.b + aib.t] : null;
		if (typeof str === "string") {
			var data = str.split(",");
			if (data.length === 4 && +data[0] === (Cfg.hideBySpell ? spells.hash : 0) && data[1] in pByNum && pByNum[data[1]].count === +data[2]) {
				sVis = data[3].split("");
				return;
			}
		}
		sVis = [];
	}

	function saveUserPosts() {
		spawn(getStored, "DESU_Posts_" + aib.dm).then(function (str) {
			var obj;
			try {
				obj = JSON.parse(str || "{}") || {};
			} catch (e) {
				obj = {};
			}
			if (str && str.length > 1000000) {
				var minDate = Date.now() - 5 * 24 * 3600 * 1000;
				for (var b in obj) {
					if (obj.hasOwnProperty(b)) {
						var vis = obj[b];
						for (var key in vis) {
							if (vis.hasOwnProperty(key) && vis[key][1] < minDate) {
								delete vis[key];
							}
						}
					}
				}
			}
			obj[aib.b] = uVis;
			setStored("DESU_Posts_" + aib.dm, JSON.stringify(obj));
			toggleContent("hid", true);
		});
	}

	function saveHiddenThreads(updContent) {
		setStored("DESU_Threads_" + aib.dm, JSON.stringify(hThr));
		if (updContent) {
			toggleContent("hid", true);
		}
	}

	function saveFavorites(fav) {
		setStored("DESU_Favorites", JSON.stringify(fav));
		toggleContent("fav", true, fav);
	}

	function removeFavoriteEntry(fav, h, b, num, clearPage) {
		function _isEmpty(f) {
			for (var i in f) {
				if (i !== "url" && f.hasOwnProperty(i)) {
					return false;
				}
			}
			return true;
		}
		if (h in fav && b in fav[h] && num in fav[h][b]) {
			delete fav[h][b][num];
			if (_isEmpty(fav[h][b])) {
				delete fav[h][b];
				if ($isEmpty(fav[h])) {
					delete fav[h];
				}
			}
		}
		if (clearPage && h === aib.host && b === aib.b && num in pByNum) {
			pByNum[num].thr.setFavBtn(false);
		}
	}

	function readViewedPosts() {
		if (!Cfg.markViewed) {
			return;
		}
		var data = sesStorage["de-viewed"];
		if (data) {
			data.split(",").forEach(function (pNum) {
				var post = pByNum[pNum];
				if (post) {
					post.el.classList.add("de-viewed");
					post.viewed = true;
				}
			});
		}
	}




	function pButton(id, href, hasHotkey) {
		return "<li><a id=\"de-btn-" + id + "\" class=\"de-abtn\" " + (hasHotkey ? "de-" : "") + "title=\"" + Lng.panelBtn[id][lang] + "\" href=\"" + href + "\"></a></li>";
	}

	function addPanel(formEl) {
		var panel,
		    evtObject,
		    imgLen = $Q(aib.qThumbImages, formEl).length,
		    b = aib.b,
		    isThr = aib.t;
		(pr && pr.pArea[0] || formEl).insertAdjacentHTML("beforebegin", "<div id=\"de-main\" lang=\"" + getThemeLang() + "\">" + "<div id=\"de-panel\">" + "<span id=\"de-btn-logo\" title=\"" + Lng.panelBtn.attach[lang] + "\"></span>" + "<ul id=\"de-panel-btns\"" + (Cfg.expandPanel ? ">" : " style=\"display: none\">") + (Cfg.disabled ? pButton("enable", "#", false) : pButton("settings", "#", true) + pButton("hidden", "#", true) + pButton("favor", "#", true) + (!Cfg.addYouTube ? "" : pButton("video", "#", true)) + (localRun ? "" : pButton("refresh", "#", false) + (!isThr && aib.page === aib.firstPage ? "" : pButton("goback", aib.getPageUrl(b, aib.page - 1), true)) + (isThr || aib.page === aib.lastPage ? "" : pButton("gonext", aib.getPageUrl(b, aib.page + 1), true))) + pButton("goup", "#", false) + pButton("godown", "#", false) + (imgLen === 0 ? "" : pButton("expimg", "#", false) + pButton("maskimg", "#", true) + (nav.Presto || localRun ? "" : (Cfg.preLoadImgs ? "" : pButton("preimg", "#", false)) + (!isThr ? "" : pButton("savethr", "#", false)))) + (!isThr || localRun ? "" : pButton(Cfg.ajaxUpdThr ? "upd-on" : "upd-off", "#", false) + (nav.Safari ? "" : pButton("audio-off", "#", false))) + (!aib.mak && !aib.tiny && !aib.fch ? "" : pButton("catalog", aib.prot + "//" + aib.host + "/" + (aib.mak ? "makaba/makaba.fcgi?task=catalog&board=" + b : b + "/catalog.html"), false)) + pButton("enable", "#", false) + (!isThr ? "" : "<span id=\"de-panel-info\" title=\"" + Lng.panelBtn.counter[lang] + "\">" + dForm.firstThr.pcount + "/" + imgLen + "</span>")) + "</ul>" + "</div><div class=\"de-content\"></div>" + (Cfg.disabled ? "" : "<div id=\"de-alert\"></div><hr style=\"clear: both;\">") + "</div>");
		panel = $id("de-panel");
		evtObject = {
			attach: false,
			odelay: 0,
			panel: panel,
			handleEvent: function handleEvent(e) {
				switch (e.type) {
					case "click":
						switch (e.target.id) {
							case "de-btn-logo":
								if (Cfg.expandPanel) {
									this.panel.lastChild.style.display = "none";
									this.attach = false;
								} else {
									this.attach = true;
								}
								toggleCfg("expandPanel");
								return;
							case "de-btn-settings":
								this.attach = toggleContent("cfg", false);break;
							case "de-btn-hidden":
								this.attach = toggleContent("hid", false);break;
							case "de-btn-favor":
								this.attach = toggleContent("fav", false);break;
							case "de-btn-video":
								this.attach = toggleContent("vid", false);break;
							case "de-btn-refresh":
								window.location.reload();break;
							case "de-btn-goup":
								scrollTo(0, 0);break;
							case "de-btn-godown":
								scrollTo(0, doc.body.scrollHeight || doc.body.offsetHeight);break;
							case "de-btn-expimg":
								isExpImg = !isExpImg;
								$del($c("de-img-center", doc));
								for (var post = dForm.firstThr.op; post; post = post.next) {
									post.toggleImages(isExpImg);
								}
								break;
							case "de-btn-preimg":
								isPreImg = !isPreImg;
								if (!e.ctrlKey) {
									preloadImages(null);
								}
								break;
							case "de-btn-maskimg":
								toggleCfg("maskImgs");
								updateCSS();
								break;
							case "de-btn-upd-on":
							case "de-btn-upd-off":
							case "de-btn-upd-warn":
								if (updater.enabled) {
									updater.disable();
								} else {
									updater.enable();
								}
								break;
							case "de-btn-audio-on":
							case "de-btn-audio-off":
								if (updater.toggleAudio(0)) {
									updater.enable();
									e.target.id = "de-btn-audio-on";
								} else {
									e.target.id = "de-btn-audio-off";
								}
								$del($c("de-menu", doc));
								break;
							case "de-btn-savethr":
								break;
							case "de-btn-enable":
								toggleCfg("disabled");
								window.location.reload();
								break;
							default:
								return;
						}
						$pd(e);
						return;
					case "mouseover":
						if (!Cfg.expandPanel) {
							clearTimeout(this.odelay);
							this.panel.lastChild.style.display = "";
						}
						switch (e.target.id) {
							case "de-btn-settings":
								KeyEditListener.setTitle(e.target, 10);break;
							case "de-btn-hidden":
								KeyEditListener.setTitle(e.target, 7);break;
							case "de-btn-favor":
								KeyEditListener.setTitle(e.target, 6);break;
							case "de-btn-video":
								KeyEditListener.setTitle(e.target, 18);break;
							case "de-btn-goback":
								KeyEditListener.setTitle(e.target, 4);break;
							case "de-btn-gonext":
								KeyEditListener.setTitle(e.target, 17);break;
							case "de-btn-maskimg":
								KeyEditListener.setTitle(e.target, 9);break;
							case "de-btn-refresh":
								if (isThr) {
									return;
								}
														case "de-btn-savethr":
							case "de-btn-audio-off":
								addMenu(e);
						}
						return;
					default:
					
						if (!Cfg.expandPanel && !this.attach) {
							this.odelay = setTimeout(function (obj) {
								obj.panel.lastChild.style.display = "none";
								obj.attach = false;
							}, 500, this);
						}
						switch (e.target.id) {
							case "de-btn-refresh":
							case "de-btn-savethr":
							case "de-btn-audio-off":
								removeMenu(e);
						}
				}
			}
		};
		panel.addEventListener("click", evtObject, true);
		panel.addEventListener("mouseover", evtObject, false);
		panel.addEventListener("mouseout", evtObject, false);
	}

	function toggleContent(name, isUpd, data) {
		var remove,
		    el = $c("de-content", doc),
		    id = "de-content-" + name;
		if (!el) {
			return false;
		}
		if (isUpd && el.id !== id) {
			return true;
		}
		remove = !isUpd && el.id === id;
		if (el.hasChildNodes() && Cfg.animation) {
			nav.animEvent(el, function (node) {
				showContent(node, id, name, remove, data);
				id = name = remove = data = null;
			});
			el.className = "de-content de-cfg-close";
		} else {
			showContent(el, id, name, remove, data);
		}
		return !remove;
	}

	function addContentBlock(parent, title) {
		return parent.appendChild($New("div", { "class": "de-content-block" }, [$new("input", { type: "checkbox" }, { click: function () {
				var _this = this;

				$each($Q(".de-entry > input", this.parentNode), function (el) {
					return el.checked = _this.checked;
				});
			} }), title]));
	}

	function showContent(cont, id, name, remove, data) {
		var temp, cfgTabId;
		if (name === "cfg" && !remove && (temp = $q(".de-cfg-tab-back[selected=\"true\"] > .de-cfg-tab", cont))) {
			cfgTabId = temp.getAttribute("info");
		}
		cont.innerHTML = cont.style.backgroundColor = "";
		if (remove) {
			cont.removeAttribute("id");
			return;
		}
		cont.id = id;
		if (Cfg.attachPanel && name !== "cfg") {
			cont.style.backgroundColor = getComputedStyle(doc.body).getPropertyValue("background-color");
		}
		switch (name) {
			case "fav":
				if (data) {
					showFavoriteTable(cont, data);
				} else {
				
					readFav().then(function (fav) {
						showFavoriteTable(cont, fav);
					});
				}
				return;
			case "cfg":
				addSettings(cont, cfgTabId);break;
			case "hid":
				showHiddenTable(cont);break;
			case "vid":
				showVideosTable(cont);
		}
		if (Cfg.animation) {
			cont.className = "de-content de-cfg-open";
		}
	}

	function showVideosTable(cont) {
		var els = $C("de-video-link", dForm.el);
		if (!els.length) {
			cont.innerHTML = "<b>" + Lng.noVideoLinks[lang] + "</b>";
			return;
		}
		if (!$id("de-ytube-api")) {
			doc.head.appendChild($new("script", {
				id: "de-ytube-api",
				type: "text/javascript",
				src: aib.prot + "//www.youtube.com/player_api"
			}, null));
		}
		cont.innerHTML = "<div de-disableautoplay class=\"de-video-obj\"></div>" + "<center>" + "<a class=\"de-abtn\" id=\"de-video-btn-prev\" href=\"#\" title=\"" + Lng.prevVideo[lang] + "\">" + "&#x25C0;" + "</a> " + "<a class=\"de-abtn\" id=\"de-video-btn-hide\" href=\"#\" title=\"" + Lng.hideLnkList[lang] + "\">" + "&#x25B2;" + "</a> " + "<a class=\"de-abtn\" id=\"de-video-btn-next\" href=\"#\" title=\"" + Lng.nextVideo[lang] + "\">" + "&#x25B6;" + "</a>" + "</center>" + "<div id=\"de-video-list\" style=\"max-width: " + (+Cfg.YTubeWidth + 40) + "px; max-height: " + (doc.documentElement.clientHeight - +Cfg.YTubeHeigh - 110) + "px;\"></div>";
		var linkList = cont.lastChild;
		$before(linkList, $new("script", { type: "text/javascript", text: "\n\t\t(function() {\n\t\t\tif('YT' in window && 'Player' in window.YT) {\n\t\t\t\tonYouTubePlayerAPIReady();\n\t\t\t} else {\n\t\t\t\twindow.onYouTubePlayerAPIReady = onYouTubePlayerAPIReady;\n\t\t\t}\n\t\t\tfunction onYouTubePlayerAPIReady() {\n\t\t\t\twindow.de_addVideoEvents =\n\t\t\t\t\taddEvents.bind(document.querySelector('#de-content-vid > .de-video-obj'));\n\t\t\t\twindow.de_addVideoEvents();\n\t\t\t}\n\t\t\tfunction addEvents() {\n\t\t\t\tvar autoplay = true;\n\t\t\t\tif(this.hasAttribute('de-disableautoplay')) {\n\t\t\t\t\tautoplay = false;\n\t\t\t\t\tthis.removeAttribute('de-disableautoplay');\n\t\t\t\t}\n\t\t\t\tnew YT.Player(this.firstChild, { events: {\n\t\t\t\t\t'onError': gotoNextVideo,\n\t\t\t\t\t'onReady': autoplay ? function(e) {\n\t\t\t\t\t\te.target.playVideo();\n\t\t\t\t\t} : Function.prototype,\n\t\t\t\t\t'onStateChange': function(e) {\n\t\t\t\t\t\tif(e.data === 0) {\n\t\t\t\t\t\t\tgotoNextVideo();\n\t\t\t\t\t\t}\n\t\t\t\t\t}\n\t\t\t\t}});\n\t\t\t}\n\t\t\tfunction gotoNextVideo() {\n\t\t\t\tdocument.getElementById(\"de-video-btn-next\").click();\n\t\t\t}\n\t\t})();\n\t" }));
		cont.addEventListener("click", {
			linkList: linkList,
			listHidden: false,
			player: cont.firstChild,
			playerInfo: null,
			currentLink: null,
			handleEvent: function handleEvent(e) {
				var el = e.target;
				if (el.classList.contains("de-abtn")) {
					var node;
					switch (e.target.id) {
						case "de-video-btn-hide":
							if (this.listHidden) {
								this.linkList.style.display = "";
								e.target.textContent = "▲";
							} else {
								this.linkList.style.display = "none";
								e.target.textContent = "▼";
							}
							this.listHidden = !this.listHidden;
							$pd(e);
							return;
						case "de-video-btn-prev":
							node = this.currentLink.parentNode;
							node = node.previousSibling || node.parentNode.lastChild;
							break;
						case "de-video-btn-next":
							node = this.currentLink.parentNode;
							node = node.nextSibling || node.parentNode.firstChild;
					}
					node.firstChild.click();
					$pd(e);
					return;
				} else if (!el.classList.contains("de-video-link")) {
					return;
				}
				var m = el.videoInfo;
				if (this.playerInfo !== m) {
					if (this.currentLink) {
						this.currentLink.classList.remove("de-current");
					}
					this.currentLink = el;
					el.classList.add("de-current");
					this.playerInfo = m;
					Videos.addPlayer(this.player, m, el.classList.contains("de-ytube"), true);
				}
				$pd(e);
			}
		}, true);
		for (var i = 0, len = els.length; i < len; ++i) {
			var el = els[i].cloneNode(true);
			el.videoInfo = els[i].videoInfo;
			linkList.insertAdjacentHTML("beforeend", "<div class=\"de-entry " + aib.cReply + "\"></div>");
			linkList.lastChild.appendChild(el);
			el.classList.remove("de-current");
			if (i === 0) {
				el.click();
			}
			el.setAttribute("onclick", "window.de_addVideoEvents && window.de_addVideoEvents();");
		}
	}

	function showHiddenTable(cont) {
		var block,
		    els = $C("de-post-hide", dForm.el);
		for (var i = 0, len = els.length; i < len; ++i) {
			var post = els[i];
			if (post.isOp) {
				continue;
			}
			var cln = post.cloneNode(true);
			cln.removeAttribute("id");
			cln.style.display = "";
			cln.className = aib.cReply + " de-post-hide de-cloned-post";
			cln.post = Object.create(cln.clone = post.post);
			cln.post.el = cln;
			cln.btn = $q(".de-btn-hide, .de-btn-hide-user", cln);
			cln.btn.parentNode.className = "de-post-btns";
			cln.btn.onclick = (function () {
				this.hideContent(this.hidden = !this.hidden);
			}).bind(cln.post);
			if (!block) {
				block = cont.appendChild($add("<div class=\"de-content-block\"><b>" + Lng.hiddenPosts[lang] + ":</b></div>"));
			}
			block.appendChild($New("div", { "class": "de-entry" }, [cln]));
		}
		if (block) {
			cont.appendChild($btn(Lng.expandAll[lang], "", function () {
				$each($Q(".de-cloned-post", this.parentNode), function (el) {
					var post = el.post;
					post.hideContent(post.hidden = !post.hidden);
				});
				this.value = this.value === Lng.undo[lang] ? Lng.expandAll[lang] : Lng.undo[lang];
			}));
			cont.appendChild($btn(Lng.save[lang], "", function () {
				$each($Q(".de-cloned-post", this.parentNode), (function (date, el) {
					if (!el.post.hidden) {
						el.clone.setUserVisib(false, date, true);
					}
				}).bind(null, Date.now()));
				saveUserPosts();
			}));
		} else {
			cont.insertAdjacentHTML("beforeend", "<b>" + Lng.noHidPosts[lang] + "</b>");
		}
		cont.insertAdjacentHTML("beforeend", "<hr><b>" + ($isEmpty(hThr) ? Lng.noHidThrds[lang] : Lng.hiddenThrds[lang] + ":") + "</b>");
		for (var b in hThr) {
			if (!$isEmpty(hThr[b])) {
				block = addContentBlock(cont, $new("b", { text: "/" + b }, null));
				for (var tNum in hThr[b]) {
					block.insertAdjacentHTML("beforeend", "<div class=\"de-entry " + aib.cReply + "\" info=\"" + b + ";" + tNum + "\"><input type=\"checkbox\"><a href=\"" + aib.getThrdUrl(b, tNum) + "\" target=\"_blank\">" + tNum + "</a> - " + hThr[b][tNum] + "</div>");
				}
			}
		}
		cont.insertAdjacentHTML("beforeend", "<hr>");
		cont.appendChild(addEditButton("hidden", function (fn) {
			fn(hThr, true, function (data) {
				hThr = data;
				if (!(aib.b in hThr)) {
					hThr[aib.b] = {};
				}
				dForm.firstThr.updateHidden(hThr[aib.b]);
				saveHiddenThreads(true);
				locStorage["__de-threads"] = JSON.stringify(hThr);
				locStorage.removeItem("__de-threads");
			});
		}));
		cont.appendChild($btn(Lng.clear[lang], Lng.clrDeleted[lang], async(regeneratorRuntime.mark(function callee$2$0() {
			var _this = this;

			var i, els, len, _els$i$getAttribute$split, _els$i$getAttribute$split2, board, tNum;

			return regeneratorRuntime.wrap(function callee$2$0$(context$3$0) {
				while (1) switch (context$3$0.prev = context$3$0.next) {
					case 0:
						i = 0, els = $Q(".de-entry[info]", _this.parentNode), len = els.length;

					case 1:
						if (!(i < len)) {
							context$3$0.next = 17;
							break;
						}

						_els$i$getAttribute$split = els[i].getAttribute("info").split(";");
						_els$i$getAttribute$split2 = _slicedToArray(_els$i$getAttribute$split, 2);
						board = _els$i$getAttribute$split2[0];
						tNum = _els$i$getAttribute$split2[1];
						context$3$0.prev = 6;
						context$3$0.next = 9;
						return $ajax(aib.getThrdUrl(board, tNum));

					case 9:
						context$3$0.next = 14;
						break;

					case 11:
						context$3$0.prev = 11;
						context$3$0.t8 = context$3$0["catch"](6);

						if (context$3$0.t8.status === 404) {
							delete hThr[board][tNum];
							saveHiddenThreads(true);
						}

					case 14:
						++i;
						context$3$0.next = 1;
						break;

					case 17:
					case "end":
						return context$3$0.stop();
				}
			}, callee$2$0, this, [[6, 11]]);
		}))));
		cont.appendChild($btn(Lng.remove[lang], Lng.clrSelected[lang], function () {
			$each($Q(".de-entry[info]", this.parentNode), (function (date, el) {
				if ($t("input", el).checked) {
					var arr = el.getAttribute("info").split(";");
					if (arr[1] in pByNum) {
						pByNum[arr[1]].setUserVisib(false, date, true);
					} else {
						locStorage["__de-post"] = JSON.stringify({
							brd: arr[0],
							date: date,
							isOp: true,
							num: arr[1],
							hide: false
						});
						locStorage.removeItem("__de-post");
					}
					delete hThr[arr[0]][arr[1]];
				}
			}).bind(null, Date.now()));
			saveHiddenThreads(true);
		}));
	}

	function clearFavoriteTable() {
		var els = $Q(".de-entry[de-removed]", doc),
		    len = els.length;
		if (len > 0) {
			readFav().then(function (fav) {
				for (var i = 0; i < len; ++i) {
					var el = els[i];
					removeFavoriteEntry(fav, el.getAttribute("de-host"), el.getAttribute("de-board"), el.getAttribute("de-num"), true);
				}
				saveFavorites(fav);
			});
		}
	}

	function showFavoriteTable(cont, data) {
		for (var h in data) {
			for (var b in data[h]) {
				var d = data[h][b],
				    block = addContentBlock(cont, d.url ? $new("a", { href: d.url, text: h + "/" + b }, null) : $new("b", { text: h + "/" + b }, null));
				if (h === aib.host && b === aib.b) {
					block.classList.add("de-fav-current");
				}
				for (var tNum in d) {
					if (tNum === "url") {
						continue;
					}
					var t = d[tNum];
					if (!t.url.startsWith("http")) {
						t.url = (h === aib.host ? aib.prot + "//" : "http://") + h + t.url;
					}
					block.insertAdjacentHTML("beforeend", "<div class=\"de-entry " + aib.cReply + "\" de-host=\"" + h + "\" de-board=\"" + b + "\" de-num=\"" + tNum + "\" de-url=\"" + t.url + "\">" + "<input type=\"checkbox\">" + "<a href=\"" + t.url + (t.last ? aib.anchor + t.last : "") + "\">" + tNum + "</a>" + "<span class=\"de-fav-title\"> - " + t.txt + "</span>" + "<span class=\"de-fav-inf-posts\">" + "<span class=\"de-fav-inf-err\">" + (t.err || "") + "</span> " + "<span class=\"de-fav-inf-new\" title=\"" + Lng.newPosts[lang] + "\"" + (t["new"] ? ">" : " style=\"display: none;\">") + (t["new"] || 0) + "</span> " + "[<span class=\"de-fav-inf-old\" title=\"" + Lng.oldPosts[lang] + "\">" + t.cnt + "</span>] " + "<span class=\"de-fav-inf-page\" title=\"" + Lng.thrPage[lang] + "\"></span>" + "</span>" + "</div>");
				}
			}
		}
		cont.insertAdjacentHTML("afterbegin", "<b>" + Lng[cont.hasChildNodes() ? "favThrds" : "noFavThrds"][lang] + "</b>");
		cont.insertAdjacentHTML("beforeend", "<hr>");
		cont.appendChild(addEditButton("favor", function (fn) {
			readFav().then(function (val) {
				return fn(val, true, saveFavorites);
			});
		}));
		cont.appendChild($btn(Lng.refresh[lang], Lng.infoCount[lang], async(regeneratorRuntime.mark(function callee$2$0() {
			var update, els, fav, i, len, form, el, host, b, num, f, cnt;
			return regeneratorRuntime.wrap(function callee$2$0$(context$3$0) {
				while (1) switch (context$3$0.prev = context$3$0.next) {
					case 0:
						update = false;
						els = $C("de-entry", doc);
						return context$3$0.delegateYield(getStoredObj("DESU_Favorites"), "t9", 3);

					case 3:
						fav = context$3$0.t9;
						i = 0, len = els.length;

					case 5:
						if (!(i < len)) {
							context$3$0.next = 34;
							break;
						}

						el = els[i], host = el.getAttribute("de-host"), b = el.getAttribute("de-board"), num = el.getAttribute("de-num"), f = fav[host][b][num];

						if (!(host !== aib.host || f.err === "Closed")) {
							context$3$0.next = 9;
							break;
						}

						return context$3$0.abrupt("continue", 31);

					case 9:
						el = $c("de-fav-inf-new", el);
						el.style.display = "";
						el.textContent = "";
						el.className = "de-wait";
						context$3$0.prev = 13;
						context$3$0.next = 16;
						return ajaxLoad(aib.getThrdUrl(b, num));

					case 16:
						form = context$3$0.sent;
						context$3$0.next = 25;
						break;

					case 19:
						context$3$0.prev = 19;
						context$3$0.t10 = context$3$0["catch"](13);

						el.classList.remove("de-wait");
						f.err = el.previousElementSibling.textContent = getErrorMessage(context$3$0.t10);
						update = true;
						return context$3$0.abrupt("continue", 31);

					case 25:
						if (f.err) {
							delete f.err;
							el.previousElementSibling.textContent = "";
							update = true;
						}
						cnt = $Q(aib.qRPost, form).length + 1 - el.nextElementSibling.textContent;

						el.textContent = cnt;
						el.className = "de-fav-inf-new";
						if (cnt === 0) {
							el.style.display = "none";
						} else {
							f["new"] = cnt;
							update = true;
						}
						if ($q(aib.qClosed, form)) {
							f.err = el.previousElementSibling.textContent = "Closed";
							update = true;
						}

					case 31:
						++i;
						context$3$0.next = 5;
						break;

					case 34:
						if (update) {
							setStored("DESU_Favorites", JSON.stringify(fav));
						}

					case 35:
					case "end":
						return context$3$0.stop();
				}
			}, callee$2$0, this, [[13, 19]]);
		}))));
		cont.appendChild($btn(Lng.page[lang], Lng.infoPage[lang], async(regeneratorRuntime.mark(function callee$2$1() {
			var els, infoCount, postsInfo, i, el, page, infoLoaded, endPage, form, tNums, pInfo, _postsInfo$i, node, isFound;

			return regeneratorRuntime.wrap(function callee$2$1$(context$3$0) {
				while (1) switch (context$3$0.prev = context$3$0.next) {
					case 0:
						els = $Q(".de-fav-current > .de-entry", doc), infoCount = els.length, postsInfo = [];

						if (infoCount) {
							context$3$0.next = 3;
							break;
						}

						return context$3$0.abrupt("return");

					case 3:
						$alert(Lng.loading[lang], "load-pages", true);
						for (i = 0; i < infoCount; ++i) {
							el = els[i];

							postsInfo.push([+el.getAttribute("de-num"), el = $c("de-fav-inf-page", el), false]);
							el.classList.add("de-wait");
						}
						page = 0, infoLoaded = 0, endPage = (aib.lastPage || 10) + 1;

					case 6:
						if (!(page < endPage)) {
							context$3$0.next = 23;
							break;
						}

						context$3$0.prev = 7;
						context$3$0.next = 10;
						return ajaxLoad(aib.getPageUrl(aib.b, page));

					case 10:
						context$3$0.t11 = context$3$0.sent;
						form = new DelForm(context$3$0.t11, true);
						context$3$0.next = 17;
						break;

					case 14:
						context$3$0.prev = 14;
						context$3$0.t12 = context$3$0["catch"](7);
						return context$3$0.abrupt("continue", 20);

					case 17:
						for (i = 0, tNums = form.tNums; i < infoCount; ++i) {
							pInfo = postsInfo[i];

							if (tNums.indexOf(pInfo[0]) !== -1) {
								pInfo[1].classList.remove("de-wait");
								pInfo[1].textContent = "@" + page;
								pInfo[2] = true;
								infoLoaded++;
							}
						}

						if (!(infoLoaded === infoCount)) {
							context$3$0.next = 20;
							break;
						}

						return context$3$0.abrupt("break", 23);

					case 20:
						++page;
						context$3$0.next = 6;
						break;

					case 23:
						for (i = 0; i < infoCount; ++i) {
							_postsInfo$i = _slicedToArray(postsInfo[i], 3);
							node = _postsInfo$i[1];
							isFound = _postsInfo$i[2];

							if (!isFound) {
								node.classList.remove("de-wait");
								node.textContent = "@?";
							}
						}
						closeAlert($id("de-alert-load-pages"));

					case 25:
					case "end":
						return context$3$0.stop();
				}
			}, callee$2$1, this, [[7, 14]]);
		}))));
		cont.appendChild($btn(Lng.clear[lang], Lng.clrDeleted[lang], async(regeneratorRuntime.mark(function callee$2$2() {
			var i, els, len, el, node;
			return regeneratorRuntime.wrap(function callee$2$2$(context$3$0) {
				while (1) switch (context$3$0.prev = context$3$0.next) {
					case 0:
						i = 0, els = $C("de-entry", doc), len = els.length;

					case 1:
						if (!(i < len)) {
							context$3$0.next = 17;
							break;
						}

						el = els[i], node = $c("de-fav-inf-err", el);

						node.classList.add("de-wait");
						context$3$0.prev = 4;
						context$3$0.next = 7;
						return $ajax(el.getAttribute("de-url"), null, false);

					case 7:
						context$3$0.next = 13;
						break;

					case 9:
						context$3$0.prev = 9;
						context$3$0.t13 = context$3$0["catch"](4);

						if (context$3$0.t13.status === 404) {
							el.setAttribute("de-removed", "");
						}
						node.textContent = getErrorMessage(new AjaxError(context$3$0.t13.status, context$3$0.t13.statusText));

					case 13:
						node.classList.remove("de-wait");

					case 14:
						++i;
						context$3$0.next = 1;
						break;

					case 17:
						clearFavoriteTable();

					case 18:
					case "end":
						return context$3$0.stop();
				}
			}, callee$2$2, this, [[4, 9]]);
		}))));
		cont.appendChild($btn(Lng.remove[lang], Lng.clrSelected[lang], function () {
			$each($C("de-entry", doc), function (el) {
				if ($t("input", el).checked) {
					el.setAttribute("de-removed", "");
				}
			});
			clearFavoriteTable();
		}));
		if (Cfg.animation) {
			cont.className = "de-content de-cfg-open";
		}
	}




	function fixSettings() {
		function toggleBox(state, arr) {
			var i = arr.length,
			    nState = !state;
			while (i--) {
				($q(arr[i], doc) || {}).disabled = nState;
			}
		}
		toggleBox(Cfg.ajaxUpdThr, ["input[info=\"updThrDelay\"]", "input[info=\"noErrInTitle\"]", "input[info=\"favIcoBlink\"]", "input[info=\"markNewPosts\"]", "input[info=\"desktNotif\"]", "input[info=\"updCount\"]"]);
		toggleBox(Cfg.expandImgs, ["input[info=\"imgNavBtns\"]", "input[info=\"resizeDPI\"]", "input[info=\"resizeImgs\"]", "input[info=\"minImgSize\"]", "input[info=\"zoomFactor\"]", "input[info=\"webmControl\"]", "input[info=\"webmVolume\"]"]);
		toggleBox(Cfg.preLoadImgs, ["input[info=\"findImgFile\"]"]);
		toggleBox(Cfg.linksNavig, ["input[info=\"linksOver\"]", "input[info=\"linksOut\"]", "input[info=\"markViewed\"]", "input[info=\"strikeHidd\"]", "input[info=\"noNavigHidd\"]"]);
		toggleBox(Cfg.strikeHidd && Cfg.linksNavig === 2, ["input[info=\"removeHidd\"]"]);
		toggleBox(Cfg.addYouTube && Cfg.addYouTube !== 4, ["select[info=\"YTubeType\"]", "input[info=\"addVimeo\"]"]);
		toggleBox(Cfg.addYouTube, ["input[info=\"YTubeWidth\"]", "input[info=\"YTubeHeigh\"]", "input[info=\"YTubeTitles\"]", "input[info=\"ytApiKey\"]"]);
		toggleBox(Cfg.YTubeTitles, ["input[info=\"ytApiKey\"]"]);
		toggleBox(Cfg.ajaxReply, ["input[info=\"sendErrNotif\"]", "input[info=\"scrAfterRep\"]"]);
		toggleBox(Cfg.ajaxReply === 2, ["input[info=\"postSameImg\"]", "input[info=\"removeEXIF\"]", "input[info=\"removeFName\"]"]);
		toggleBox(Cfg.addTextBtns, ["input[info=\"txtBtnsLoc\"]"]);
		toggleBox(Cfg.updScript, ["select[info=\"scrUpdIntrv\"]"]);
		toggleBox(Cfg.hotKeys, ["input[info=\"loadPages\"]"]);
	}

	function lBox(id, isBlock, fn) {
		var el = $new("input", { info: id, type: "checkbox" }, { click: function () {
				toggleCfg(this.getAttribute("info"));
				fixSettings();
				if (fn) {
					fn(this);
				}
			} });
		el.checked = Cfg[id];
		return $New("label", isBlock ? { "class": "de-block" } : null, [el, $txt(" " + Lng.cfg[id][lang])]);
	}

	function inpTxt(id, size, Fn) {
		return $new("input", { info: id, type: "text", size: size, value: Cfg[id] }, {
			keyup: Fn ? Fn : function () {
				saveCfg(this.getAttribute("info"), this.value);
			}
		});
	}

	function optSel(id, isBlock, Fn) {
		var el,
		    opt = "",
		    x = Lng.cfg[id];
		for (var i = 0, len = x.sel[lang].length; i < len; i++) {
			opt += "<option value=\"" + i + "\">" + x.sel[lang][i] + "</option>";
		}
		el = $add("<select info=\"" + id + "\">" + opt + "</select>");
		el.addEventListener("change", Fn || function () {
			saveCfg(this.getAttribute("info"), this.selectedIndex);
			fixSettings();
		}, false);
		el.selectedIndex = Cfg[id];
		return $New("label", isBlock ? { "class": "de-block" } : null, [el, $txt(" " + x.txt[lang])]);
	}

	function cfgTab(name) {
		return $New("div", { "class": aib.cReply + " de-cfg-tab-back", selected: false }, [$new("div", {
			"class": "de-cfg-tab",
			text: Lng.cfgTab[name][lang],
			info: name }, {
			click: function () {
				var el = this.parentNode;
				if (el.getAttribute("selected") === "true") {
					return;
				}
				var prefTab = $c("de-cfg-body", doc);
				if (prefTab) {
					prefTab.className = "de-cfg-unvis";
					$q(".de-cfg-tab-back[selected=\"true\"]", doc).setAttribute("selected", false);
				}
				el.setAttribute("selected", true);
				var id = this.getAttribute("info"),
				    newTab = $id("de-cfg-" + id);
				if (!newTab) {
					$after($id("de-cfg-bar"), newTab = id === "filters" ? getCfgFilters() : id === "posts" ? getCfgPosts() : id === "images" ? getCfgImages() : id === "links" ? getCfgLinks() : id === "form" ? getCfgForm() : id === "common" ? getCfgCommon() : getCfgInfo());
					if (id === "filters") {
						updRowMeter($id("de-spell-edit"));
					}
				}
				newTab.className = "de-cfg-body";
				if (id === "filters") {
					$id("de-spell-edit").value = spells.list;
				}
				fixSettings();
			}
		})]);
	}

	function updRowMeter(node) {
		var top = node.scrollTop,
		    el = node.parentNode.previousSibling.firstChild,
		    num = el.numLines || 1,
		    i = 15;
		if (num - i < (top / 12 | 0 + 1)) {
			var str = "";
			while (i--) {
				str += num++ + "<br>";
			}
			el.insertAdjacentHTML("beforeend", str);
			el.numLines = num;
		}
		el.scrollTop = top;
	}

	function getCfgFilters() {
		return $New("div", { "class": "de-cfg-unvis", id: "de-cfg-filters" }, [lBox("hideBySpell", false, toggleSpells), $New("div", { id: "de-spell-panel" }, [$new("a", {
			id: "de-btn-addspell",
			text: Lng.add[lang],
			href: "#",
			"class": "de-abtn" }, {
			click: $pd,
			mouseover: addMenu,
			mouseout: removeMenu
		}), $new("a", { text: Lng.apply[lang], href: "#", "class": "de-abtn" }, { click: function (e) {
				$pd(e);
				saveCfg("hideBySpell", 1);
				$q("input[info=\"hideBySpell\"]", doc).checked = true;
				toggleSpells();
			} }), $new("a", { text: Lng.clear[lang], href: "#", "class": "de-abtn" }, { click: function (e) {
				$pd(e);
				$id("de-spell-edit").value = "";
				toggleSpells();
			} }), $add("<a href=\"https://github.com/SthephanShinkufag/Dollchan-Extension-Tools/wiki/Spells-" + (lang ? "en" : "ru") + "\" class=\"de-abtn\" target=\"_blank\">[?]</a>")]), $New("div", { id: "de-spell-div" }, [$add("<div><div id=\"de-spell-rowmeter\"></div></div>"), $New("div", null, [$new("textarea", { id: "de-spell-edit", wrap: "off" }, {
			keydown: function () {
				updRowMeter(this);
			},
			scroll: function () {
				updRowMeter(this);
			}
		})])]), lBox("sortSpells", true, function () {
			if (Cfg.sortSpells) {
				toggleSpells();
			}
		}), lBox("menuHiddBtn", true, null), lBox("hideRefPsts", true, null), lBox("delHiddPost", true, function () {
			$each($C("de-post-hide", dForm.el), function (el) {
				el.post.wrap.classList.toggle("de-hidden");
			});
			updateCSS();
		})]);
	}

	function getCfgPosts() {
		return $New("div", { "class": "de-cfg-unvis", id: "de-cfg-posts" }, [$if(!localRun, $New("div", null, [lBox("ajaxUpdThr", false, aib.t ? function () {
			if (Cfg.ajaxUpdThr) {
				updater.enable();
			} else {
				updater.disable();
			}
		} : null), $New("label", null, [inpTxt("updThrDelay", 2, null), $txt(Lng.cfg.updThrDelay[lang])]), $New("div", { "class": "de-cfg-depend" }, [lBox("noErrInTitle", true, null), lBox("favIcoBlink", true, null), lBox("markNewPosts", true, function () {
			dForm.firstThr.clearPostsMarks();
		}), $if("Notification" in window, lBox("desktNotif", true, function () {
			if (Cfg.desktNotif) {
				Notification.requestPermission();
			}
		})), lBox("updCount", true, function () {
			updater.toggleCounter(Cfg.updCount);
		})])])), lBox("expandTrunc", true, updateCSS), optSel("postBtnsCSS", false, null), lBox("showHideBtn", false, updateCSS), lBox("showRepBtn", false, updateCSS), lBox("noSpoilers", true, updateCSS), lBox("noPostNames", true, updateCSS), lBox("widePosts", true, updateCSS), $New("div", null, [lBox("correctTime", false, DateTime.toggleSettings), inpTxt("timeOffset", 2, null), $txt(Lng.cfg.timeOffset[lang]), $add("<a href=\"https://github.com/SthephanShinkufag/Dollchan-Extension-Tools/wiki/Settings-time-" + (lang ? "en" : "ru") + "\" class=\"de-abtn\" target=\"_blank\">[?]</a>")]), $New("div", { "class": "de-cfg-depend" }, [$New("div", null, [inpTxt("timePattern", 25, null), $txt(Lng.cfg.timePattern[lang])]), $New("div", null, [inpTxt("timeRPattern", 25, null), $txt(Lng.cfg.timeRPattern[lang])])])]);
	}

	function getCfgImages() {
		return $New("div", { "class": "de-cfg-unvis", id: "de-cfg-images" }, [optSel("expandImgs", true, null), $New("div", { "class": "de-cfg-depend" }, [lBox("imgNavBtns", true, updateCSS), lBox("resizeImgs", true, null), $if(Post.sizing.dPxRatio > 1, lBox("resizeDPI", true, null)), $New("div", null, [inpTxt("minImgSize", 4, function () {
			saveCfg("minImgSize", Math.max(+this.value, 1));
		}), $txt(Lng.cfg.minImgSize[lang])]), inpTxt("zoomFactor", 4, function () {
			saveCfg("zoomFactor", Math.min(Math.max(+this.value, 1), 100));
		}), $txt(Lng.cfg.zoomFactor[lang]), lBox("webmControl", true, null), $if(nav.canPlayWebm, $New("div", null, [inpTxt("webmVolume", 4, function () {
			saveCfg("webmVolume", Math.min(+this.value, 100));
		}), $txt(Lng.cfg.webmVolume[lang])]))]), $if(!nav.Presto, lBox("preLoadImgs", true, null)), $if(!nav.Presto && !aib.fch, $New("div", { "class": "de-cfg-depend" }, [lBox("findImgFile", true, null)])), optSel("openImgs", true, null), lBox("imgSrcBtns", true, null), lBox("delImgNames", true, null)]);
	}

	function getCfgLinks() {
		return $New("div", { "class": "de-cfg-unvis", id: "de-cfg-links" }, [optSel("linksNavig", true, null), $New("div", { "class": "de-cfg-depend" }, [$New("div", null, [inpTxt("linksOver", 4, function () {
			saveCfg("linksOver", +this.value | 0);
		}), $txt(Lng.cfg.linksOver[lang]), inpTxt("linksOut", 4, function () {
			saveCfg("linksOut", +this.value | 0);
		}), $txt(Lng.cfg.linksOut[lang])]), lBox("markViewed", true, null), lBox("strikeHidd", true, null), $New("div", { "class": "de-cfg-depend" }, [lBox("removeHidd", false, updateCSS)]), lBox("noNavigHidd", true, null)]), lBox("crossLinks", true, null), lBox("insertNum", true, null), lBox("addOPLink", true, null), lBox("addImgs", true, null), lBox("addMP3", false, null), $if(aib.prot === "http:", lBox("addVocaroo", false, null)), optSel("addYouTube", true, null), $New("div", { "class": "de-cfg-depend" }, [$New("div", null, [optSel("YTubeType", false, null), inpTxt("YTubeWidth", 4, null), $txt("×"), inpTxt("YTubeHeigh", 4, null)]), $if(!nav.Opera11 || nav.isGM, lBox("YTubeTitles", false, null)), $if(!nav.Opera11 || nav.isGM, $New("div", null, [inpTxt("ytApiKey", 25, function () {
			saveCfg("ytApiKey", this.value.trim());
		}), $txt(" " + Lng.cfg.ytApiKey[lang])])), lBox("addVimeo", true, null)])]);
	}

	function getCfgForm() {
		return $New("div", { "class": "de-cfg-unvis", id: "de-cfg-form" }, [optSel("ajaxReply", true, null), $if(pr.form, $New("div", { "class": "de-cfg-depend" }, [$if(!nav.Opera11, $New("div", null, [lBox("postSameImg", true, null), lBox("removeEXIF", false, null), lBox("removeFName", false, null), lBox("sendErrNotif", true, null)])), lBox("scrAfterRep", true, null)])), $if(pr.form, optSel("addPostForm", true, function () {
			saveCfg("addPostForm", this.selectedIndex);
			pr.isBottom = Cfg.addPostForm === 1;
			pr.setReply(false, !aib.t || Cfg.addPostForm > 1);
		})), $if(pr.txta, lBox("spacedQuote", true, null)), lBox("favOnReply", true, null), $if(pr.subj, lBox("warnSubjTrip", false, null)), $if(pr.file && !nav.Presto, lBox("fileThumb", true, function () {
			for (var inp = pr.fileObj; true; inp = inp.next) {
				inp.updateUtils();
				if (!inp.next) {
					break;
				}
			}
			if (inp.empty) {
				inp.hideInputs();
			}
			if (!aib.kus && !aib.multiFile) {
				pr.setPlaceholders();
			}
			updateCSS();
		})), $if(!aib.iich && pr.mail, $New("div", null, [lBox("addSageBtn", false, null), lBox("saveSage", false, null)])), $if(pr.capTr, optSel("captchaLang", true, null)), $if(pr.txta, $New("div", null, [optSel("addTextBtns", false, function () {
			saveCfg("addTextBtns", this.selectedIndex);
			pr.addTextPanel();
		}), lBox("txtBtnsLoc", false, pr.addTextPanel.bind(pr))])), $if(pr.passw, $New("div", null, [inpTxt("passwValue", 15, PostForm.setUserPassw), $txt(Lng.cfg.userPassw[lang]), $btn(Lng.change[lang], "", function () {
			$q("input[info=\"passwValue\"]", doc).value = Math.round(Math.random() * 1000000000000000).toString(32);
			PostForm.setUserPassw();
		})])), $if(pr.name, $New("div", null, [inpTxt("nameValue", 15, PostForm.setUserName), $txt(" "), lBox("userName", false, PostForm.setUserName)])), $New("div", null, [$txt(Lng.dontShow[lang]), lBox("noBoardRule", false, updateCSS), $if(pr.gothr, lBox("noGoto", false, function () {
			$disp(pr.gothr);
		})), $if(pr.passw, lBox("noPassword", false, function () {
			$disp(pr.passw.parentNode.parentNode);
		})), $if(pr.name, lBox("noName", false, function () {
			$disp(pr.name.parentNode.parentNode);
		}))])]);
	}

	function getCfgCommon() {
		return $New("div", { "class": "de-cfg-unvis", id: "de-cfg-common" }, [optSel("scriptStyle", true, function () {
			saveCfg("scriptStyle", this.selectedIndex);
			$id("de-main").lang = $id("de-qarea").firstChild.nextSibling.lang = getThemeLang();
		}), $New("div", null, [lBox("userCSS", false, updateCSS), addEditButton("css", function (fn) {
			fn(Cfg.userCSSTxt, false, function () {
				saveCfg("userCSSTxt", this.value);
				updateCSS();
				toggleContent("cfg", true);
			});
		})]), lBox("attachPanel", true, function () {
			toggleContent("cfg", false);
			updateCSS();
		}), lBox("panelCounter", true, updateCSS), lBox("rePageTitle", true, null), $if(nav.Anim, lBox("animation", true, null)), lBox("closePopups", true, null), $New("div", null, [lBox("hotKeys", false, function () {
			if (Cfg.hotKeys) {
				if (hKeys) {
					hKeys.enable();
				} else {
					hKeys = new HotKeys();
				}
			} else if (hKeys) {
				hKeys.disable();
			}
		}), $btn(Lng.edit[lang], "", function (e) {
			$pd(e);
			if ($id("de-alert-edit-hotkeys")) {
				return;
			}
			spawn(HotKeys.readKeys).then(function (keys) {
				var el,
				    fn,
				    temp = KeyEditListener.getEditMarkup(keys);
				$alert(temp[1], "edit-hotkeys", false);
				el = $id("de-alert-edit-hotkeys");
				fn = new KeyEditListener(el, keys, temp[0]);
				el.addEventListener("focus", fn, true);
				el.addEventListener("blur", fn, true);
				el.addEventListener("click", fn, true);
				el.addEventListener("keydown", fn, true);
				el.addEventListener("keyup", fn, true);
			});
		})]), $New("div", { "class": "de-cfg-depend" }, [inpTxt("loadPages", 2, null), $txt(Lng.cfg.loadPages[lang])]), $if(!nav.isChromeStorage && !nav.Presto || nav.isGM, $New("div", null, [lBox("updScript", true, null), $New("div", { "class": "de-cfg-depend" }, [optSel("scrUpdIntrv", false, null), $btn(Lng.checkNow[lang], "", function () {
			$alert(Lng.loading[lang], "updavail", true);
			spawn(getStoredObj, "DESU_Config").then(function (val) {
				return checkForUpdates(true, val.lastUpd);
			}).then(function (html) {
				return $alert(html, "updavail", false);
			}, emptyFn);
		})])])), $if(nav.isGlobal, $New("div", null, [$txt(Lng.cfg.excludeList[lang]), $new("input", { type: "text", id: "de-exclude-edit", size: 45, style: "display: block;",
			value: excludeList }, {
			keyup: function () {
				setStored("DESU_Exclude", this.value);
			}
		}), lBox("turnOff", true, function () {
			spawn(getStoredObj, "DESU_Config").then(function (val) {
				for (var dm in val) {
					if (dm !== aib.dm && dm !== "global" && dm !== "lastUpd") {
						val[dm].disabled = Cfg.turnOff;
					}
				}
				val[aib.dm].turnOff = Cfg.turnOff;
				setStored("DESU_Config", JSON.stringify(val));
			});
		})]))]);
	}

	function getCfgInfo() {
		function getHiddenThrCount() {
			var count = 0;
			for (var b in hThr) {
				for (var tNum in hThr[b]) {
					count++;
				}
			}
			return count;
		}
		return $New("div", { "class": "de-cfg-unvis", id: "de-cfg-info" }, [$add("<div style=\"padding-bottom: 10px;\">" + "<a href=\"https://github.com/SthephanShinkufag/Dollchan-Extension-Tools/wiki/versions\" " + "target=\"_blank\">v" + version + "</a>&nbsp;|&nbsp;" + "<a href=\"http://www.freedollchan.org/scripts/\" target=\"_blank\">Freedollchan</a>&nbsp;|&nbsp;" + "<a href=\"https://github.com/SthephanShinkufag/Dollchan-Extension-Tools/wiki/" + (lang ? "home-en/" : "") + "\" target=\"_blank\">Github</a></div>"), $add("<div><table class=\"de-cfg-info-data\"><tbody>" + "<tr><td>" + Lng.thrViewed[lang] + "</td><td>" + Cfg.stats.view + "</td></tr>" + "<tr><td>" + Lng.thrCreated[lang] + "</td><td>" + Cfg.stats.op + "</td></tr>" + "<tr><td>" + Lng.thrHidden[lang] + "</td><td>" + getHiddenThrCount() + "</td></tr>" + "<tr><td>" + Lng.postsSent[lang] + "</td><td>" + Cfg.stats.reply + "</td></tr></tbody></table>" + "<table class=\"de-cfg-info-data\" style=\"border-left: 1px solid grey;\">" + new Logger().getTable() + "</table></div>"), $btn(Lng.debug[lang], Lng.infoDebug[lang], function () {
			$alert(Lng.infoDebug[lang] + ":<textarea readonly id=\"de-debug-info\" class=\"de-editor\"></textarea>", "help-debug", false);
			$id("de-debug-info").value = JSON.stringify({
				version: version,
				location: String(window.location),
				nav: nav,
				cfg: Cfg,
				sSpells: spells.list.split("\n"),
				oSpells: sesStorage["de-spells-" + aib.b + (aib.t || "")],
				perf: new Logger().getData(true)
			}, function (key, value) {
				switch (key) {
					case "stats":
					case "nameValue":
					case "passwValue":
					case "ytApiKey":
						return void 0;
				}
				if (key in defaultCfg && value === defaultCfg[key]) {
					return void 0;
				}
				return value;
			}, "\t");
		})]);
	}

	function addEditButton(name, getDataFn) {
		return $btn(Lng.edit[lang], Lng.editInTxt[lang], (function (getData) {
			getData(function (val, isJSON, saveFn) {
				var el,
				    ta = $new("textarea", {
					"class": "de-editor",
					value: isJSON ? JSON.stringify(val, null, "\t") : val
				}, null);
				$alert("", "edit-" + name, false);
				el = $c("de-alert-msg", $id("de-alert-edit-" + name));
				el.appendChild($txt(Lng.editor[name][lang]));
				el.appendChild(ta);
				el.appendChild($btn(Lng.save[lang], Lng.saveChanges[lang], isJSON ? (function (fun) {
					var data;
					try {
						data = JSON.parse(this.value.trim().replace(/[\n\r\t]/g, "") || "{}");
					} finally {
						if (data) {
							fun(data);
							closeAlert($id("de-alert-edit-" + name));
							closeAlert($id("de-alert-err-invaliddata"));
						} else {
							$alert(Lng.invalidData[lang], "err-invaliddata", false);
						}
					}
				}).bind(ta, saveFn) : saveFn.bind(ta)));
			});
		}).bind(null, getDataFn));
	}

	function addSettings(Set, id) {
		Set.appendChild($New("div", { "class": aib.cReply }, [$new("div", { "class": "de-cfg-head", text: "Dollchan Extension Tools" }, null), $New("div", { id: "de-cfg-bar" }, [cfgTab("filters"), cfgTab("posts"), cfgTab("images"), cfgTab("links"), $if(pr.form || pr.oeForm, cfgTab("form")), cfgTab("common"), cfgTab("info")]), $New("div", { id: "de-cfg-btns" }, [optSel("language", false, function () {
			saveCfg("language", lang = this.selectedIndex);
			$del($id("de-main"));
			$del($id("de-css"));
			$del($id("de-css-dynamic"));
			scriptCSS();
			addPanel(dForm.el);
			toggleContent("cfg", false);
		}), $New("div", { style: "float: right;" }, [addEditButton("cfg", function (fn) {
			fn(Cfg, true, function (data) {
				saveComCfg(aib.dm, data);
				window.location.reload();
			});
		}), $if(nav.isGlobal, $btn(Lng.load[lang], Lng.loadGlobal[lang], function () {
			spawn(getStoredObj, "DESU_Config").then(function (val) {
				if (val && "global" in val && !$isEmpty(val.global)) {
					delete val[aib.dm];
					setStored("DESU_Config", JSON.stringify(val));
					window.location.reload();
				} else {
					$alert(Lng.noGlobalCfg[lang], "err-noglobalcfg", false);
				}
			});
		})), $if(nav.isGlobal, $btn(Lng.save[lang], Lng.saveGlobal[lang], function () {
			spawn(getStoredObj, "DESU_Config").then(function (val) {
				var obj = {},
				    com = val[aib.dm];
				for (var i in com) {
					if (i !== "correctTime" && i !== "timePattern" && i !== "userCSS" && i !== "userCSSTxt" && com[i] !== defaultCfg[i] && i !== "stats") {
						obj[i] = com[i];
					}
				}
				val.global = obj;
				setStored("DESU_Config", JSON.stringify(val));
				toggleContent("cfg", true);
			});
		})), $btn(Lng.reset[lang], Lng.resetCfg[lang], function () {
			if (confirm(Lng.conReset[lang])) {
				delStored("DESU_Config");
				delStored("DESU_Favorites");
				delStored("DESU_Posts_" + aib.dm);
				delStored("DESU_Threads_" + aib.dm);
				delStored("DESU_keys");
				window.location.reload();
			}
		})]), $new("div", { style: "clear: both;" }, null)])]));
		$q(".de-cfg-tab[info=\"" + (id || "filters") + "\"]", Set).click();
	}




	function closeAlert(el) {
		if (el) {
			el.closeTimeout = null;
			if (!Cfg.animation) {
				$del(el);
				return;
			}
			nav.animEvent(el, function (node) {
				var p = node && node.parentNode;
				if (p) {
					p.removeChild(node);
				}
			});
			el.classList.add("de-close");
		}
	}

	function $alert(txt, id, wait) {
		var node,
		    el = $id("de-alert-" + id),
		    cBtn = "de-alert-btn" + (wait ? " de-wait" : ""),
		    tBtn = wait ? "" : "✖ ";
		if (el) {
			$t("div", el).innerHTML = txt.trim();
			node = $t("span", el);
			node.className = cBtn;
			node.textContent = tBtn;
			clearTimeout(el.closeTimeout);
			if (!wait && Cfg.animation) {
				nav.animEvent(el, function (node) {
					node.classList.remove("de-blink");
				});
				el.classList.add("de-blink");
			}
		} else {
			el = $id("de-alert").appendChild($New("div", { "class": aib.cReply, id: "de-alert-" + id }, [$new("span", { "class": cBtn, text: tBtn }, { click: function () {
					closeAlert(this.parentNode);
				} }), $add("<div class=\"de-alert-msg\">" + txt.trim() + "</div>")]));
			if (Cfg.animation) {
				nav.animEvent(el, function (node) {
					node.classList.remove("de-open");
				});
				el.classList.add("de-open");
			}
		}
		if (Cfg.closePopups && !wait && !id.includes("help") && !id.includes("edit")) {
			el.closeTimeout = setTimeout(closeAlert, 4000, el);
		}
	}

	function showMenu(el, html, inPanel, onclick) {
		var y,
		    pos,
		    menu,
		    cr = el.getBoundingClientRect();
		if (Cfg.attachPanel && inPanel) {
			pos = "fixed";
			y = "bottom: 25";
		} else {
			pos = "absolute";
			y = "top: " + (window.pageYOffset + cr.bottom);
		}
		doc.body.insertAdjacentHTML("beforeend", "<div class=\"" + aib.cReply + " de-menu\" style=\"position: " + pos + "; right: " + (doc.documentElement.clientWidth - cr.right - window.pageXOffset) + "px; " + y + "px;\">" + html + "</div>");
		menu = doc.body.lastChild;
		menu.addEventListener("mouseover", function (e) {
			clearTimeout(e.currentTarget.odelay);
		}, true);
		menu.addEventListener("mouseout", removeMenu, true);
		menu.addEventListener("click", (function (e) {
			var el = e.target;
			if (el.className === "de-menu-item") {
				setTimeout(this, 10, el);
				do {
					el = el.parentElement;
				} while (!el.classList.contains("de-menu"));
				$del(el);
			}
		}).bind(onclick), false);
	}

	function addMenu(e) {
		e.target.odelay = setTimeout(function (el) {
			switch (el.id) {
				case "de-btn-addspell":
					showMenu(el, "<div style=\"display: inline-block; border-right: 1px solid grey;\">" + "<span class=\"de-menu-item\">" + "#words,#exp,#exph,#imgn,#ihash,#subj,#name,#trip,#img,<br>".split(",").join("</span><span class=\"de-menu-item\">") + "</span></div><div style=\"display: inline-block;\"><span class=\"de-menu-item\">" + "#sage,#op,#tlen,#all,#video,#vauthor,#num,#wipe,#rep,#outrep".split(",").join("</span><span class=\"de-menu-item\">") + "</span></div>", false, function (el) {
						var exp = el.textContent;
						$txtInsert($id("de-spell-edit"), exp + (!aib.t || exp === "#op" || exp === "#rep" || exp === "#outrep" ? "" : "[" + aib.b + "," + aib.t + "]") + (Spells.needArg[Spells.names.indexOf(exp.substr(1))] ? "(" : ""));
					});
					return;
				case "de-btn-refresh":
					showMenu(el, "<span class=\"de-menu-item\">" + Lng.selAjaxPages[lang].join("</span><span class=\"de-menu-item\">") + "</span>", true, function (el) {
						loadPages(aProto.indexOf.call(el.parentNode.children, el) + 1);
					});
					return;
				case "de-btn-savethr":
					showMenu(el, "<span class=\"de-menu-item\">" + Lng.selSaveThr[lang].join("</span><span class=\"de-menu-item\">") + "</span>", true, function (el) {
						if (!$id("de-alert-savethr")) {
							var imgOnly = !!aProto.indexOf.call(el.parentNode.children, el);
							if (Images_.preloading) {
								$alert(Lng.loading[lang], "savethr", true);
								Images_.afterpreload = loadDocFiles.bind(null, imgOnly);
								Images_.progressId = "savethr";
							} else {
								loadDocFiles(imgOnly);
							}
						}
					});
					return;
				case "de-btn-audio-off":
					showMenu(el, "<span class=\"de-menu-item\">" + Lng.selAudioNotif[lang].join("</span><span class=\"de-menu-item\">") + "</span>", true, function (el) {
						var i = aProto.indexOf.call(el.parentNode.children, el);
						updater.enable();
						updater.toggleAudio(i === 0 ? 30000 : i === 1 ? 60000 : i === 2 ? 120000 : 300000);
						$id("de-btn-audio-off").id = "de-btn-audio-on";
					});
			}
		}, Cfg.linksOver, e.target);
	}

	function removeMenu(e) {
		var el = $c("de-menu", doc),
		    rt = e.relatedTarget;
		clearTimeout(e.target.odelay);
		if (el && (!rt || rt !== el && !el.contains(rt))) {
			el.odelay = setTimeout($del, 75, el);
		}
	}




	function HotKeys() {
		var _this = this;

		spawn(HotKeys.readKeys).then(function (keys) {
			return _this._init(keys);
		});
	}
	HotKeys.version = 7;
	HotKeys.readKeys = regeneratorRuntime.mark(function callee$1$0() {
		var keys, str, tKeys, mapFunc;
		return regeneratorRuntime.wrap(function callee$1$0$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					return context$2$0.delegateYield(getStored("DESU_keys"), "t14", 1);

				case 1:
					str = context$2$0.t14;

					if (str) {
						context$2$0.next = 4;
						break;
					}

					return context$2$0.abrupt("return", HotKeys.getDefaultKeys());

				case 4:
					context$2$0.prev = 4;

					keys = JSON.parse(str);

				case 6:
					context$2$0.prev = 6;

					if (keys) {
						context$2$0.next = 9;
						break;
					}

					return context$2$0.abrupt("return", HotKeys.getDefaultKeys());

				case 9:
					if (keys[0] !== HotKeys.version) {
						tKeys = HotKeys.getDefaultKeys();

						switch (keys[0]) {
							case 1:
								keys[2][11] = tKeys[2][11];
								keys[4] = tKeys[4];
														case 2:
								keys[2][12] = tKeys[2][12];
								keys[2][13] = tKeys[2][13];
								keys[2][14] = tKeys[2][14];
								keys[2][15] = tKeys[2][15];
								keys[2][16] = tKeys[2][16];
														case 3:
								keys[2][17] = keys[3][3];
								keys[3][3] = keys[3].splice(4, 1)[0];
														case 4:
							case 5:
							case 6:
								keys[2][18] = tKeys[2][18];
						}

						keys[0] = HotKeys.version;
						setStored("DESU_keys", JSON.stringify(keys));
					}
					if (keys[1] ^ !!nav.Firefox) {
						mapFunc = nav.Firefox ? function mapFuncFF(key) {
							switch (key) {
								case 189:
									return 173;
								case 187:
									return 61;
								case 186:
									return 59;
								default:
									return key;
							}
						} : function mapFuncNonFF(key) {
							switch (key) {
								case 173:
									return 189;
								case 61:
									return 187;
								case 59:
									return 186;
								default:
									return key;
							}
						};

						keys[1] = !!nav.Firefox;
						keys[2] = keys[2].map(mapFunc);
						keys[3] = keys[3].map(mapFunc);
						setStored("DESU_keys", JSON.stringify(keys));
					}
					return context$2$0.abrupt("return", keys);

				case 13:
				case "end":
					return context$2$0.stop();
			}
		}, callee$1$0, this, [[4,, 6, 13]]);
	});
	HotKeys.getDefaultKeys = function () {
		var globKeys = [
		75 ,
		74 ,
		82 ,
		72 ,
		4133 ,
		49165 ,
		16454 ,
		16456 ,
		80 ,
		66 ,
		16467 ,
		73 ,
		49218 ,
		49225 ,
		49236 ,
		49232 ,
		49219 ,
		4135 ,
		16470 		];
		var nonThrKeys = [
		77 ,
		78 ,
		86 ,
		69 		];
		var thrKeys = [
		85 		];
		return [HotKeys.version, !!nav.Firefox, globKeys, nonThrKeys, thrKeys];
	};
	HotKeys.prototype = {
		cPost: null,
		enabled: false,
		gKeys: null,
		lastPage: 0,
		lastPageOffset: 0,
		ntKeys: null,
		paused: false,
		tKeys: null,
		clear: function clear(lastPage) {
			this.cPost = null;
			this.lastPage = lastPage;
			this.lastPageOffset = 0;
		},
		disable: function disable() {
			if (this.enabled) {
				if (this.cPost) {
					this.cPost.unselect();
				}
				doc.removeEventListener("keydown", this, true);
				this.enabled = false;
			}
		},
		enable: function enable() {
			if (!this.enabled) {
				this.clear(aib.page);
				doc.addEventListener("keydown", this, true);
				this.enabled = true;
			}
		},
		handleEvent: function handleEvent(e) {
			if (this.paused || e.metaKey) {
				return;
			}
			var isThr = aib.t,
			    curTh = e.target.tagName,
			    kc = e.keyCode | (e.ctrlKey ? 4096 : 0) | (e.shiftKey ? 8192 : 0) | (e.altKey ? 16384 : 0) | (curTh === "TEXTAREA" || curTh === "INPUT" && (e.target.type === "text" || e.target.type === "password") ? 32768 : 0);
			if (kc === 116 || kc === 32884) {
			
				if (isThr || $id("de-alert-load-pages")) {
					return;
				}
				if (Attachment.viewer) {
					Attachment.viewer.close(null);
					Attachment.viewer = null;
				}
				loadPages(+Cfg.loadPages);
			} else if (kc === 27) {
			
				if (Attachment.viewer) {
					Attachment.viewer.close(null);
					Attachment.viewer = null;
					return;
				}
				if (this.cPost) {
					this.cPost.unselect();
					this.cPost = null;
				}
				if (isThr) {
					dForm.firstThr.clearPostsMarks();
				}
				this.lastPageOffset = 0;
			} else if (kc === 32795) {
			
				e.target.blur();
			} else {
				var post,
				    idx,
				    expand = null,
				    globIdx = this.gKeys.indexOf(kc);
				switch (globIdx) {
					case 2:
					
						if (pr.form) {
							post = this.cPost || this._getFirstVisPost(false, true) || dForm.firstThr.op;
							this.cPost = post;
							pr.showQuickReply(post, post.num, true, false);
							post.select();
						}
						break;
					case 3:
					
						post = this._getFirstVisPost(false, true) || this._getNextVisPost(null, true, false);
						if (post) {
							post.toggleUserVisib();
							this._scroll(post, false, post.isOp);
						}
						break;
					case 4:
					
						if (Attachment.viewer) {
							Attachment.viewer.navigate(false);
						} else if (isThr || aib.page !== aib.firstPage) {
							window.location.pathname = aib.getPageUrl(aib.b, isThr ? 0 : aib.page - 1);
						}
						break;
					case 5:
					
						if (e.target !== pr.txta && e.target !== pr.cap) {
							return;
						}
						pr.subm.click();
						break;
					case 6:
					
						expand = toggleContent("fav", false);
						break;
					case 7:
					
						expand = toggleContent("hid", false);
						break;
					case 8:
					
						$disp($id("de-panel").lastChild);
						break;
					case 9:
					
						toggleCfg("maskImgs");
						updateCSS();
						break;
					case 10:
					
						expand = toggleContent("cfg", false);
						break;
					case 11:
					
						post = this._getFirstVisPost(false, true) || this._getNextVisPost(null, true, false);
						if (post) {
							post.toggleImages(!post.imagesExpanded);
						}
						break;
					case 12:
					
						if (e.target !== pr.txta) {
							return;
						}
						$id("de-btn-bold").click();
						break;
					case 13:
					
						if (e.target !== pr.txta) {
							return;
						}
						$id("de-btn-italic").click();
						break;
					case 14:
					
						if (e.target !== pr.txta) {
							return;
						}
						$id("de-btn-strike").click();
						break;
					case 15:
					
						if (e.target !== pr.txta) {
							return;
						}
						$id("de-btn-spoil").click();
						break;
					case 16:
					
						if (e.target !== pr.txta) {
							return;
						}
						$id("de-btn-code").click();
						break;
					case 17:
					
						if (Attachment.viewer) {
							Attachment.viewer.navigate(true);
						} else if (!isThr && this.lastPage !== aib.lastPage) {
							window.location.pathname = aib.getPageUrl(aib.b, this.lastPage + 1);
						}
						break;
					case 18:
					
						expand = toggleContent("vid", false);
						break;
					case -1:
						if (isThr) {
							idx = this.tKeys.indexOf(kc);
							if (idx === 0) {
							
								updater.forceLoad(null);
								break;
							}
							return;
						}
						idx = this.ntKeys.indexOf(kc);
						if (idx === -1) {
							return;
						} else if (idx === 2) {
						
							post = this._getFirstVisPost(false, true) || this._getNextVisPost(null, true, false);
							if (post) {
								if (nav.Firefox) {
									GM_openInTab(aib.getThrdUrl(aib.b, post.tNum), false, true);
								} else {
									window.open(aib.getThrdUrl(aib.b, post.tNum), "_blank");
								}
							}
							break;
						} else if (idx === 3) {
						
							post = this._getFirstVisPost(false, true) || this._getNextVisPost(null, true, false);
							if (post) {
								if (post.thr.loadedOnce && post.thr.op.next.count === 1) {
									var nextThr = post.thr.nextNotHidden;
									post.thr.load(visPosts, !!nextThr);
									post = (nextThr || post.thr).op;
								} else {
									post.thr.load(1, false);
									post = post.thr.op;
								}
								scrollTo(0, window.pageYOffset + post.topCoord);
								if (this.cPost && this.cPost !== post) {
									this.cPost.unselect();
									this.cPost = post;
								}
							}
							break;
						}
										default:
						var scrollToThr = !isThr && (globIdx === 0 || globIdx === 1);
						this._scroll(this._getFirstVisPost(scrollToThr, false), globIdx === 0 || idx === 0, scrollToThr);
				}
				if (expand !== null && !Cfg.expandPanel) {
					$id("de-panel").lastChild.style.display = expand ? "" : "none";
				}
			}
			e.stopPropagation();
			$pd(e);
		},
		pause: function pause() {
			this.paused = true;
		},
		resume: function resume(keys) {
			this.gKeys = keys[2];
			this.ntKeys = keys[3];
			this.tKeys = keys[4];
			this.paused = false;
		},

		_getFirstVisPost: function _getFirstVisPost(getThread, getFull) {
			if (this.lastPageOffset !== window.pageYOffset) {
				var post = getThread ? dForm.firstThr : dForm.firstThr.op;
				while (post.topCoord < 1) {
					var tPost = post.next;
					if (!tPost) {
						break;
					}
					post = tPost;
				}
				if (this.cPost) {
					this.cPost.unselect();
				}
				this.cPost = getThread ? getFull ? post.op : post.op.prev : getFull ? post : post.prev;
				this.lastPageOffset = window.pageYOffset;
			}
			return this.cPost;
		},
		_getNextVisPost: function _getNextVisPost(cPost, isOp, toUp) {
			if (isOp) {
				var thr = cPost ? toUp ? cPost.thr.prevNotHidden : cPost.thr.nextNotHidden : dForm.firstThr.hidden ? dForm.firstThr.nextNotHidden : dForm.firstThr;
				return thr ? thr.op : null;
			}
			return cPost ? cPost.getAdjacentVisPost(toUp) : dForm.firstThr.hidden || dForm.firstThr.op.hidden ? dForm.firstThr.op.getAdjacentVisPost(toUp) : dForm.firstThr.op;
		},
		_init: function _init(keys) {
			this.enabled = true;
			this.lastPage = aib.page;
			this.gKeys = keys[2];
			this.ntKeys = keys[3];
			this.tKeys = keys[4];
			doc.addEventListener("keydown", this, true);
		},
		_scroll: function _scroll(post, toUp, toThread) {
			var next = this._getNextVisPost(post, toThread, toUp);
			if (!next) {
				if (!aib.t && (toUp ? aib.page > aib.firstPage : this.lastPage < aib.lastPage)) {
					window.location.pathname = aib.getPageUrl(aib.b, toUp ? aib.page - 1 : this.lastPage + 1);
				}
				return;
			}
			if (post) {
				post.unselect();
			}
			if (toThread) {
				next.el.scrollIntoView();
			} else {
				scrollTo(0, window.pageYOffset + next.el.getBoundingClientRect().top - Post.sizing.wHeight / 2 + next.el.clientHeight / 2);
			}
			this.lastPageOffset = window.pageYOffset;
			next.select();
			this.cPost = next;
		}
	};

	function KeyEditListener(alertEl, keys, allKeys) {
		var aInputs = aProto.slice.call($C("de-input-key", alertEl));
		for (var i = 0, len = allKeys.length; i < len; ++i) {
			var k = allKeys[i];
			if (k !== 0) {
				for (var j = i + 1; j < len; ++j) {
					if (k === allKeys[j]) {
						aInputs[i].classList.add("de-error-key");
						aInputs[j].classList.add("de-error-key");
						break;
					}
				}
			}
		}
		this.aEl = alertEl;
		this.keys = keys;
		this.initKeys = JSON.parse(JSON.stringify(keys));
		this.allKeys = allKeys;
		this.allInputs = aInputs;
		this.errCount = $C("de-error-key", alertEl).length;
		if (this.errCount !== 0) {
			this.saveButton.disabled = true;
		}
	}



	KeyEditListener.keyCodes = ["",,,,,,,, "Backspace", "Tab",,,, "Enter",,, "Shift", "Ctrl", "Alt",,,,,,,,,,,,,, "Space",,,,, "←", "↑", "→", "↓",,,,,,,, "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",, ";",, "=",,,, "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",,,,,, "Numpad 0", "Numpad 1", "Numpad 2", "Numpad 3", "Numpad 4", "Numpad 5", "Numpad 6", "Numpad 7", "Numpad 8", "Numpad 9", "Numpad *", "Numpad +",, "Numpad -", "Numpad .", "Numpad /",,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,, "-",,,,,,,,,,,,, ";", "=", ",", "-", ".", "/", "`",,,,,,,,,,,,,,,,,,,,,,,,,,, "[", "\\", "]", "'"];
	KeyEditListener.getStrKey = function (key) {
		var str = "";
		if (key & 4096) {
			str += "Ctrl+";
		}
		if (key & 8192) {
			str += "Shift+";
		}
		if (key & 16384) {
			str += "Alt+";
		}
		str += KeyEditListener.keyCodes[key & 4095];
		return str;
	};
	KeyEditListener.getEditMarkup = function (keys) {
		var allKeys = [];
		var html = Lng.hotKeyEdit[lang].join("").replace(/%l/g, "<label class=\"de-block\">").replace(/%\/l/g, "</label>").replace(/%i([2-4])([0-9]+)(t)?/g, (function (aKeys, all, id1, id2, isText) {
			var key = this[+id1][+id2];
			aKeys.push(key);
			return "<input class=\"de-input-key\" type=\"text\" de-id1=\"" + id1 + "\" de-id2=\"" + id2 + "\" size=\"26\" value=\"" + KeyEditListener.getStrKey(key) + (isText ? "\" de-text" : "\"") + " readonly></input>";
		}).bind(keys, allKeys)) + "<input type=\"button\" id=\"de-keys-save\" value=\"" + Lng.save[lang] + "\"></input>" + "<input type=\"button\" id=\"de-keys-reset\" value=\"" + Lng.reset[lang] + "\"></input>";
		return [allKeys, html];
	};
	KeyEditListener.setTitle = function (el, idx) {
		var title = el.getAttribute("de-title");
		if (hKeys && idx !== -1) {
			title += " [" + KeyEditListener.getStrKey(hKeys.gKeys[idx]) + "]";
		}
		el.title = title;
	};
	KeyEditListener.prototype = Object.defineProperties({
		cEl: null,
		cKey: -1,
		errorInput: false,
		handleEvent: function handleEvent(e) {
			var key,
			    el = e.target;
			switch (e.type) {
				case "blur":
					if (hKeys && this.errCount === 0) {
						hKeys.resume(this.keys);
					}
					this.cEl = null;
					return;
				case "focus":
					if (hKeys) {
						hKeys.pause();
					}
					this.cEl = el;
					return;
				case "click":
					var keys;
					if (el.id === "de-keys-reset") {
						this.keys = HotKeys.getDefaultKeys();
						this.initKeys = HotKeys.getDefaultKeys();
						if (hKeys) {
							hKeys.resume(this.keys);
						}
						var temp = KeyEditListener.getEditMarkup(this.keys);
						this.allKeys = temp[0];
						$c("de-alert-msg", this.aEl).innerHTML = temp[1];
						this.allInputs = aProto.slice.call($C("de-input-key", this.aEl));
						this.errCount = 0;
						delete this.saveButton;
						break;
					} else if (el.id === "de-keys-save") {
						keys = this.keys;
						setStored("DESU_keys", JSON.stringify(keys));
					} else if (el.className === "de-alert-btn") {
						keys = this.initKeys;
					} else {
						return;
					}
					if (hKeys) {
						hKeys.resume(keys);
					}
					closeAlert($id("de-alert-edit-hotkeys"));
					break;
				case "keydown":
					if (!this.cEl) {
						return;
					}
					key = e.keyCode;
					if (key === 27 || key === 46) {
					
						this.cEl.value = "";
						this.cKey = 0;
						this.errorInput = false;
						break;
					}
					var keyStr = KeyEditListener.keyCodes[key];
					if (keyStr === undefined) {
						this.cKey = -1;
						return;
					}
					var str = "";
					if (e.ctrlKey) {
						str += "Ctrl+";
					}
					if (e.shiftKey) {
						str += "Shift+";
					}
					if (e.altKey) {
						str += "Alt+";
					}
					if (key === 16 || key === 17 || key === 18) {
						this.errorInput = true;
					} else {
						this.cKey = key | (e.ctrlKey ? 4096 : 0) | (e.shiftKey ? 8192 : 0) | (e.altKey ? 16384 : 0) | (this.cEl.hasAttribute("de-text") ? 32768 : 0);
						this.errorInput = false;
						str += keyStr;
					}
					this.cEl.value = str;
					break;
				case "keyup":
					el = this.cEl;
					key = this.cKey;
					if (!el || key === -1) {
						return;
					}
					var rEl,
					    isError = el.classList.contains("de-error-key");
					if (!this.errorInput && key !== -1) {
						var idx = this.allInputs.indexOf(el),
						    oKey = this.allKeys[idx];
						if (oKey === key) {
							this.errorInput = false;
							break;
						}
						var rIdx = key === 0 ? -1 : this.allKeys.indexOf(key);
						this.allKeys[idx] = key;
						if (isError) {
							idx = this.allKeys.indexOf(oKey);
							if (idx !== -1 && this.allKeys.indexOf(oKey, idx + 1) === -1) {
								rEl = this.allInputs[idx];
								if (rEl.classList.contains("de-error-key")) {
									this.errCount--;
									rEl.classList.remove("de-error-key");
								}
							}
							if (rIdx === -1) {
								this.errCount--;
								el.classList.remove("de-error-key");
							}
						}
						if (rIdx === -1) {
							this.keys[+el.getAttribute("de-id1")][+el.getAttribute("de-id2")] = key;
							if (this.errCount === 0) {
								this.saveButton.disabled = false;
							}
							this.errorInput = false;
							break;
						}
						rEl = this.allInputs[rIdx];
						if (!rEl.classList.contains("de-error-key")) {
							this.errCount++;
							rEl.classList.add("de-error-key");
						}
					}
					if (!isError) {
						this.errCount++;
						el.classList.add("de-error-key");
					}
					if (this.errCount !== 0) {
						this.saveButton.disabled = true;
					}
			}
			$pd(e);
		}
	}, {
		saveButton: {
			get: function () {
				var val = $id("de-keys-save");
				Object.defineProperty(this, "saveButton", { value: val, configurable: true });
				return val;
			},
			configurable: true,
			enumerable: true
		}
	});




	function initMessageFunctions() {
		doc.defaultView.addEventListener("message", function (e) {
			if (typeof e.data === "string") {
				switch (e.data.substr(0, 15)) {
					case "de-iframe-pform":
						checkUpload($DOM(e.data.substr(15)));
						$q("iframe[name=\"de-iframe-pform\"]", doc).src = "about:blank";
						return;
					case "de-iframe-dform":
						checkDelete($DOM(e.data.substr(15)));
						$q("iframe[name=\"de-iframe-dform\"]", doc).src = "about:blank";
				}
			}
		}, false);
	}

	function detectImgFile(ab) {
		var i,
		    j,
		    dat = new Uint8Array(ab),
		    len = dat.length;
				if (dat[0] === 255 && dat[1] === 216) {
			for (i = 0, j = 0; i < len - 1; i++) {
				if (dat[i] === 255) {
										if (dat[i + 1] === 216) {
						j++;
											} else if (dat[i + 1] === 217 && --j === 0) {
						i += 2;
						break;
					}
				}
			}
					} else if (dat[0] === 137 && dat[1] === 80) {
			for (i = 0; i < len - 7; i++) {
								if (dat[i] === 73 && dat[i + 1] === 69 && dat[i + 2] === 78 && dat[i + 3] === 68) {
					i += 8;
					break;
				}
			}
		} else {
			return {};
		}
				if (i !== len && len - i > 60) {
			for (len = i + 90; i < len; i++) {
								if (dat[i] === 55 && dat[i + 1] === 122 && dat[i + 2] === 188) {
					return { type: 0, idx: i, data: ab };
									} else if (dat[i] === 80 && dat[i + 1] === 75 && dat[i + 2] === 3) {
					return { type: 1, idx: i, data: ab };
									} else if (dat[i] === 82 && dat[i + 1] === 97 && dat[i + 2] === 114) {
					return { type: 2, idx: i, data: ab };
									} else if (dat[i] === 79 && dat[i + 1] === 103 && dat[i + 2] === 103) {
					return { type: 3, idx: i, data: ab };
									} else if (dat[i] === 73 && dat[i + 1] === 68 && dat[i + 2] === 51) {
					return { type: 4, idx: i, data: ab };
				}
			}
		}
		return {};
	}

	function WorkerPool(mReqs, wrkFn, errFn) {
		if (!nav.hasWorker) {
			this.run = function (data, transferObjs, fn) {
				return fn(wrkFn(data));
			};
			return;
		}
		var url = window.URL.createObjectURL(new Blob(["self.onmessage = function(e) {\n\t\tvar info = (" + String(wrkFn) + ")(e.data);\n\t\tif(info.data) {\n\t\t\tself.postMessage(info, [info.data]);\n\t\t} else {\n\t\t\tself.postMessage(info);\n\t\t}\n\t}"], { type: "text/javascript" }));
		this._pool = new TasksPool(mReqs, this._createWorker.bind(this), null);
		this._freeWorkers = [];
		this._url = url;
		this._errFn = errFn;
		while (mReqs--) {
			this._freeWorkers.push(new Worker(url));
		}
	}
	WorkerPool.prototype = {
		run: function run(data, transferObjs, fn) {
			this._pool.run([data, transferObjs, fn]);
		},
		_createWorker: function _createWorker(num, data) {
			var _this = this;

			return new Promise(function (resolve, reject) {
				var w = _this._freeWorkers.pop();
				var _data = _slicedToArray(data, 3);

				var sendData = _data[0];
				var transferObjs = _data[1];
				var fn = _data[2];

				w.onmessage = function (e) {
					fn(e.data);
					_this._freeWorkers.push(w);
					resolve();
				};
				w.onerror = function (err) {
					resolve();
					_this._freeWorkers.push(w);
					_this._errFn(err);
				};
				w.postMessage(sendData, transferObjs);
			});
		},
		clear: function clear() {
			window.URL.revokeObjectURL(this._url);
			this._freeWorkers = [];
		}
	};

	function addImgFileIcon(aEl, fName, info) {
		var app,
		    ext,
		    type = info.type;
		if (typeof type === "undefined") {
			return;
		}
		if (type === 2) {
			app = "application/x-rar-compressed";
			ext = "rar";
		} else if (type === 1) {
			app = "application/zip";
			ext = "zip";
		} else if (type === 0) {
			app = "application/x-7z-compressed";
			ext = "7z";
		} else if (type === 3) {
			app = "audio/ogg";
			ext = "ogg";
		} else {
			app = "audio/mpeg";
			ext = "mp3";
		}
		aEl.insertAdjacentHTML("afterend", "<a href=\"" + window.URL.createObjectURL(new Blob([nav.getUnsafeUint8Array(info.data, info.idx)], { type: app })) + "\" class=\"de-img-" + (type > 2 ? "audio" : "arch") + "\" title=\"" + Lng.downloadFile[lang] + "\" download=\"" + fName.substring(0, fName.lastIndexOf(".")) + "." + ext + "\">." + ext + "</a>");
	}

	function downloadImgData(url) {
		var repeatOnError = arguments[1] === undefined ? true : arguments[1];

		return $ajax(url, {
			responseType: "arraybuffer",
			overrideMimeType: "text/plain; charset=x-user-defined"
		}, !aib.fch || url.startsWith("blob")).then(function (xhr) {
			if ("response" in xhr) {
				return nav.getUnsafeUint8Array(xhr.response);
			}
			var txt = xhr.responseText,
			    rv = new Uint8Array(txt.length);
			for (var i = 0, len = txt.length; i < len; ++i) {
				rv[i] = txt.charCodeAt(i) & 255;
			}
			return rv;
		}, function (xhr) {
			if (xhr instanceof Error || xhr.status === 404) {
				return null;
			}
			if (xhr.status === 0 && xhr.responseType === "arraybuffer") {
				return new Uint8Array(xhr.response);
			}
			return repeatOnError ? downloadImgData(url, false) : null;
		});
	}

	function preloadImages(post) {
		if (!Cfg.preLoadImgs && !Cfg.openImgs && !isPreImg) {
			return;
		}
		var pool;
		if (isPreImg || Cfg.preLoadImgs) {
			var cImg = 1,
			    mReqs = post ? 1 : 4,
			    rjf = (isPreImg || Cfg.findImgFile) && new WorkerPool(mReqs, detectImgFile, function (e) {
				console.error("FILE DETECTOR ERROR, line: " + e.lineno + " - " + e.message);
			});
			pool = new TasksPool(mReqs, function (num, data) {
				return downloadImgData(data[0]).then(function (imageData) {
					var _data = _slicedToArray(data, 5);

					var url = _data[0];
					var lnk = _data[1];
					var iType = _data[2];
					var nExp = _data[3];
					var el = _data[4];

					if (imageData) {
						var fName = url.substring(url.lastIndexOf("/") + 1),
						    aEl = $q(aib.qImgLink, aib.getImgWrap(lnk));
						aEl.setAttribute("download", fName);
						lnk.href = window.URL.createObjectURL(new Blob([imageData], { type: iType }));
						lnk.setAttribute("de-name", fName);
						if (iType === "video/webm") {
							el.setAttribute("de-video", "");
						}
						if (nExp) {
							el.src = lnk.href;
						}
						if (rjf) {
							rjf.run(imageData.buffer, [imageData.buffer], addImgFileIcon.bind(null, aEl, fName));
						}
					}
					if (Images_.progressId) {
						$alert(Lng.loadImage[lang] + cImg + "/" + len, Images_.progressId, true);
					}
					cImg++;
				});
			}, function () {
				Images_.preloading = false;
				if (Images_.afterpreload) {
					Images_.afterpreload();
					Images_.afterpreload = Images_.progressId = null;
				}
				if (rjf) {
					rjf.clear();
				}
			});
			Images_.preloading = true;
		}
		var els = $Q(aib.qThumbImages, post || dForm.el);
		for (var i = 0, len = els.length; i < len; ++i) {
			var el = els[i],
			    lnk = $parent(el = els[i], "A");
			if (!lnk) {
				continue;
			}
			var iType,
			    url = lnk.href,
			    nExp = !!Cfg.openImgs;
			if (/\.gif$/i.test(url)) {
				iType = "image/gif";
				nExp &= Cfg.openImgs !== 3;
			} else {
				if (/\.jpe?g$/i.test(url)) {
					iType = "image/jpeg";
				} else if (/\.png$/i.test(url)) {
					iType = "image/png";
				} else if (/\.webm$/i.test(url)) {
					iType = "video/webm";
					nExp = false;
				} else {
					continue;
				}
				nExp &= Cfg.openImgs !== 2;
			}
			if (pool) {
				pool.run([url, lnk, iType, nExp, el]);
			} else if (nExp) {
				el.src = url;
			}
		}
		if (pool) {
			pool.complete();
		}
	}

	function getDataFromImg(img) {
		var cnv = Images_.canvas || (Images_.canvas = doc.createElement("canvas"));
		cnv.width = img.width;
		cnv.height = img.height;
		cnv.getContext("2d").drawImage(img, 0, 0);
		return new Uint8Array(atob(cnv.toDataURL("image/png").split(",")[1]).split("").map(function (a) {
			return a.charCodeAt();
		}));
	}

	function loadDocFiles(imgOnly) {
		var els,
		    progress,
		    counter,
		    count = 0,
		    current = 1,
		    warnings = "",
		    tar = new TarBuilder(),
		    dc = imgOnly ? doc : doc.documentElement.cloneNode(true);
		Images_.pool = new TasksPool(4, function (num, data) {
			return downloadImgData(data[0]).then(function (imgData) {
				var _data = _slicedToArray(data, 4);

				var url = _data[0];
				var name = _data[1];
				var el = _data[2];
				var link = _data[3];
				var safeName = name.replace(/[\\\/:*?"<>|]/g, "_");
				progress.value = current;
				counter.innerHTML = current;
				current++;
				if (link) {
					if (!imgData) {
						warnings += "<br>" + Lng.cantLoad[lang] + "<a href=\"" + url + "\">" + url + "</a><br>" + Lng.willSavePview[lang];
						$alert(Lng.loadErrors[lang] + warnings, "floadwarn", false);
						safeName = "thumb-" + safeName.replace(/\.[a-z]+$/, ".png");
						imgData = getDataFromImg(el);
					}
					if (!imgOnly) {
						el.classList.add("de-thumb");
						link.href = $q(aib.qImgLink, aib.getImgWrap(link)).href = safeName = "images/" + safeName;
						if (safeName.match(/\.webm$/)) {
							tar.addFile(el.src = safeName.replace(/\.webm$/, ".png"), getDataFromImg(el));
						} else {
							el.src = safeName;
						}
					}
					tar.addFile(safeName, imgData);
				} else if (imgData && imgData.length > 0) {
					tar.addFile(el.href = el.src = "data/" + safeName, imgData);
				} else {
					$del(el);
				}
			});
		}, function () {
			var u,
			    a,
			    name = aib.dm + "-" + aib.b.replace(/[\\\/:*?"<>|]/g, "") + "-" + aib.t;
			if (!imgOnly) {
				var dt = doc.doctype;
				$t("head", dc).insertAdjacentHTML("beforeend", "<script type=\"text/javascript\" src=\"data/dollscript.js\"></script>");
				tar.addString("data/dollscript.js", "(" + String(typeof de_main_func_outer === "undefined" ? de_main_func_inner : de_main_func_outer) + ")(null, true);");
				tar.addString(name + ".html", "<!DOCTYPE " + dt.name + (dt.publicId ? " PUBLIC \"" + dt.publicId + "\"" : dt.systemId ? " SYSTEM" : "") + (dt.systemId ? " \"" + dt.systemId + "\"" : "") + ">" + dc.outerHTML);
			}
			u = window.URL.createObjectURL(tar.get());
			a = $new("a", { href: u, download: name + (imgOnly ? "-images.tar" : ".tar") }, null);
			doc.body.appendChild(a);
			a.click();
			setTimeout(function (el, url) {
				window.URL.revokeObjectURL(url);
				$del(el);
			}, 100000, a, u);
			$del($id("de-alert-filesload"));
			Images_.pool = tar = warnings = count = current = imgOnly = progress = counter = null;
		});
		els = aProto.slice.call($Q(aib.qThumbImages, $q("[de-form]", dc)));
		count += els.length;
		els.forEach(function (el) {
			var lnk = $parent(el, "A");
			if (lnk) {
				var url = lnk.href;
				if (aib.tiny) {
					url = url.replace(/^.*?\?v=|&.*?$/g, "");
				}
				Images_.pool.run([url, lnk.getAttribute("de-name") || url.substring(url.lastIndexOf("/") + 1), el, lnk]);
			}
		});
		if (!imgOnly) {
			$each($Q("#de-main, .de-parea, .de-post-btns, .de-btn-src, #de-qarea, .de-refmap, #de-updater-div," + " .de-video-obj, link[rel=\"alternate stylesheet\"], script, " + aib.qPostForm, dc), $del);
			$each($T("a", dc), function (el) {
				var num,
				    tc = el.textContent;
				if (tc[0] === ">" && tc[1] === ">" && (num = +tc.substr(2)) && num in pByNum) {
					el.href = aib.anchor + num;
				} else {
					el.href = getAbsLink(el.href);
				}
				if (!el.classList.contains("de-link-pref")) {
					el.className = "de-link-pref " + el.className;
				}
			});
			$each($Q(aib.qRPost, dc), function (post, i) {
				post.setAttribute("de-num", i === 0 ? aib.t : aib.getPNum(post));
			});
			var files = [];
			$each($Q("link, *[src]", dc), (function (el) {
				if (els.indexOf(el) !== -1) {
					return;
				}
				var name,
				    url = el.tagName === "LINK" ? el.href : el.src;
				if (!this.test(url)) {
					$del(el);
					return;
				}
				name = url.substring(url.lastIndexOf("/") + 1).replace(/[\\\/:*?"<>|]/g, "_").toLowerCase();
				if (files.indexOf(name) !== -1) {
					var temp = url.lastIndexOf("."),
					    ext = url.substring(temp);
					url = url.substring(0, temp);
					name = name.substring(0, name.lastIndexOf("."));
					for (var i = 0;; ++i) {
						temp = name + "(" + i + ")" + ext;
						if (files.indexOf(temp) === -1) {
							break;
						}
					}
					name = temp;
				}
				files.push(name);
				Images_.pool.run([url, name, el, null]);
				count++;
			}).bind(new RegExp("^\\/\\/?|^https?:\\/\\/([^\\/]*.)?" + regQuote(aib.dm) + "\\/", "i")));
		}
		$alert((imgOnly ? Lng.loadImage[lang] : Lng.loadFile[lang]) + "<br><progress id=\"de-loadprogress\" value=\"0\" max=\"" + count + "\"></progress> <span>1</span>/" + count, "filesload", true);
		progress = $id("de-loadprogress");
		counter = progress.nextElementSibling;
		Images_.pool.complete();
		els = null;
	}




	function DateTime(pattern, rPattern, diff, dtLang, onRPat) {
		if (DateTime.checkPattern(pattern)) {
			this.disabled = true;
			return;
		}
		this.regex = pattern.replace(/(?:[sihdny]\?){2,}/g, function (str) {
			return "(?:" + str.replace(/\?/g, "") + ")?";
		}).replace(/\-/g, "[^<]").replace(/\+/g, "[^0-9]").replace(/([sihdny]+)/g, "($1)").replace(/[sihdny]/g, "\\d").replace(/m|w/g, "([a-zA-Zа-яА-Я]+)");
		this.pattern = pattern.replace(/[\?\-\+]+/g, "").replace(/([a-z])\1+/g, "$1");
		this.diff = parseInt(diff, 10);
		this.arrW = Lng.week[dtLang];
		this.arrM = Lng.month[dtLang];
		this.arrFM = Lng.fullMonth[dtLang];
		if (rPattern) {
			this.genDateTime = DateTime.genRFunc(rPattern, diff);
		} else {
			this.onRPat = onRPat;
		}
	}
	DateTime.toggleSettings = function (el) {
		if (el.checked && (!/^[+-]\d{1,2}$/.test(Cfg.timeOffset) || DateTime.checkPattern(Cfg.timePattern))) {
			$alert(Lng.cTimeError[lang], "err-correcttime", false);
			saveCfg("correctTime", 0);
			el.checked = false;
		}
	};
	DateTime.checkPattern = function (val) {
		return !val.includes("i") || !val.includes("h") || !val.includes("d") || !val.includes("y") || !(val.includes("n") || val.includes("m")) || /[^\?\-\+sihdmwny]|mm|ww|\?\?|([ihdny]\?)\1+/.test(val);
	};
	DateTime.genRFunc = function (rPattern, diff) {
		return new Function("dtime", "return '" + rPattern.replace("_o", (diff < 0 ? "" : "+") + diff).replace("_s", "' + this.pad2(dtime.getSeconds()) + '").replace("_i", "' + this.pad2(dtime.getMinutes()) + '").replace("_h", "' + this.pad2(dtime.getHours()) + '").replace("_d", "' + this.pad2(dtime.getDate()) + '").replace("_w", "' + this.arrW[dtime.getDay()] + '").replace("_n", "' + this.pad2(dtime.getMonth() + 1) + '").replace("_m", "' + this.arrM[dtime.getMonth()] + '").replace("_M", "' + this.arrFM[dtime.getMonth()] + '").replace("_y", "' + ('' + dtime.getFullYear()).substring(2) + '").replace("_Y", "' + dtime.getFullYear() + '") + "';");
	};
	DateTime.prototype = {
		genDateTime: null,
		onRPat: null,
		getRPattern: function getRPattern(txt) {
			var m = txt.match(new RegExp(this.regex));
			if (!m) {
				this.disabled = true;
				return false;
			}
			var rPattern = "";
			for (var i = 1, len = m.length, j = 0, str = m[0]; i < len;) {
				var a = m[i++],
				    p = this.pattern[i - 2];
				if ((p === "m" || p === "y") && a.length > 3) {
					p = p.toUpperCase();
				}
				var k = str.indexOf(a, j);
				rPattern += str.substring(j, k) + "_" + p;
				j = k + a.length;
			}
			if (this.onRPat) {
				this.onRPat(rPattern);
			}
			this.genDateTime = DateTime.genRFunc(rPattern, this.diff);
			return true;
		},
		pad2: function pad2(num) {
			return num < 10 ? "0" + num : num;
		},
		fix: function fix(txt) {
			var _this = this;

			if (this.disabled || !this.genDateTime && !this.getRPattern(txt)) {
				return txt;
			}
			return txt.replace(new RegExp(this.regex, "g"), function (str) {
				for (var _len = arguments.length, args = Array(_len > 1 ? _len - 1 : 0), _key = 1; _key < _len; _key++) {
					args[_key - 1] = arguments[_key];
				}

				var second, minute, hour, day, month, year;
				for (var i = 0; i < 7; ++i) {
					var a = args[i];
					switch (_this.pattern[i]) {
						case "s":
							second = a;break;
						case "i":
							minute = a;break;
						case "h":
							hour = a;break;
						case "d":
							day = a;break;
						case "n":
							month = a - 1;break;
						case "y":
							year = a;break;
						case "m":
							switch (a.slice(0, 3)) {
								case "янв":case "jan":
									month = 0;break;
								case "фев":case "feb":
									month = 1;break;
								case "мар":case "mar":
									month = 2;break;
								case "апр":case "apr":
									month = 3;break;
								case "май":case "мая":case "may":
									month = 4;break;
								case "июн":case "jun":
									month = 5;break;
								case "июл":case "jul":
									month = 6;break;
								case "авг":case "aug":
									month = 7;break;
								case "сен":case "sep":
									month = 8;break;
								case "окт":case "oct":
									month = 9;break;
								case "ноя":case "nov":
									month = 10;break;
								case "дек":case "dec":
									month = 11;break;
								default:
									month = 0;break;
							}
					}
				}
				var dtime = new Date(year.length === 2 ? "20" + year : year, month, day, hour, minute, second || 0);
				dtime.setHours(dtime.getHours() + _this.diff);
				return _this.genDateTime(dtime);
			});
		}
	};




	function Videos(post) {
		var player = arguments[1] === undefined ? null : arguments[1];
		var playerInfo = arguments[2] === undefined ? null : arguments[2];

		this.post = post;
		this.vData = [[], []];
		if (player && playerInfo) {
			Object.defineProperties(this, {
				player: { value: player },
				playerInfo: { writable: true, value: playerInfo }
			});
		}
	}
	Videos._global = Object.defineProperties({}, {
		vData: {
			get: function () {
				var val;
				try {
					val = Cfg.YTubeTitles ? JSON.parse(sesStorage["de-videos-data1"] || "[{}, {}]") : [{}, {}];
				} catch (e) {
					val = [{}, {}];
				}
				Object.defineProperty(this, "vData", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		}
	});
	Videos.ytReg = /^https?:\/\/(?:www\.|m\.)?youtu(?:be\.com\/(?:watch\?.*?v=|v\/|embed\/)|\.be\/)([a-zA-Z0-9-_]+).*?(?:t(?:ime)?=(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s?)?)?$/;
	Videos.vimReg = /^https?:\/\/(?:www\.)?vimeo\.com\/(?:[^\?]+\?clip_id=|.*?\/)?(\d+).*?(#t=\d+)?$/;
	Videos.addPlayer = function (el, m, isYtube) {
		var enableJsapi = arguments[3] === undefined ? false : arguments[3];

		var txt,
		    wh = " width=\"" + Cfg.YTubeWidth + "\" height=\"" + Cfg.YTubeHeigh + "\">";
		if (isYtube) {
			var list = m[0].match(/list=[^&#]+/);
			txt = "<iframe frameborder=\"0\" allowfullscreen=\"1\" src=\"https://www.youtube.com/embed/" + m[1] + "?start=" + ((m[2] ? m[2] * 3600 : 0) + (m[3] ? m[3] * 60 : 0) + (m[4] ? +m[4] : 0)) + (enableJsapi ? "&enablejsapi=1" : Cfg.addYouTube === 3 ? "&autoplay=1" : "") + (list ? "&" + list[0] : "") + (Cfg.YTubeType === 1 ? "&html5=1\" type=\"text/html\"" : "\" type=\"application/x-shockwave-flash\"") + wh + "</iframe>";
		} else {
			var id = m[1] + (m[2] ? m[2] : "");
			txt = Cfg.YTubeType === 1 ? "<iframe src=\"" + aib.prot + "//player.vimeo.com/video/" + id + (Cfg.addYouTube === 3 ? "?autoplay=1" : "") + "\" frameborder=\"0\" " + "webkitallowfullscreen mozallowfullscreen allowfullscreen" + wh + "</iframe>" : "<embed type=\"application/x-shockwave-flash\" src=\"" + aib.prot + "//vimeo.com/moogaloop.swf" + "?clip_id=" + id + (Cfg.addYouTube === 3 ? "&autoplay=1" : "") + "&server=vimeo.com&color=00adef&fullscreen=1\" " + "allowscriptaccess=\"always\" allowfullscreen=\"true\"" + wh + "</embed>";
		}
		el.innerHTML = txt + "<span class=\"de-video-resizer\" title=\"" + Lng.expandVideo[lang] + "\"></span>";
		el.lastChild.onclick = function () {
			var node = this.parentNode,
			    exp = node.className === "de-video-obj";
			node.className = exp ? "de-video-obj de-video-expanded" : "de-video-obj";
			if (node.parentNode.id === "de-content-vid") {
				node = node.nextSibling.nextSibling.nextSibling;
				node.style.maxWidth = (exp ? 888 : +Cfg.YTubeWidth + 40) + "px";
				node.style.maxHeight = doc.documentElement.clientHeight - (exp ? 590 : +Cfg.YTubeHeigh + 110) + "px";
			}
		};
	};
	Videos._titlesLoaderHelper = function (_ref, num) {
		for (var _len = arguments.length, data = Array(_len > 2 ? _len - 2 : 0), _key = 2; _key < _len; _key++) {
			data[_key - 2] = arguments[_key];
		}

		var _ref2 = _slicedToArray(_ref, 4);

		var link = _ref2[0];
		var isYtube = _ref2[1];
		var videoObj = _ref2[2];
		var id = _ref2[3];

		if (data.length !== 0) {
			var title = data[0];
			var author = data[1];
			var views = data[2];
			var publ = data[3];

			link.textContent = title;
			link.setAttribute("de-author", author);
			link.classList.add("de-video-title");
			link.title = Lng.author[lang] + author + (views ? ", " + Lng.views[lang] + views : "") + (publ ? ", " + Lng.published[lang] + publ : "");
			Videos._global.vData[isYtube ? 0 : 1][id] = data;
			videoObj.vData[isYtube ? 0 : 1].push(data);
			if (videoObj.titleLoadFn) {
				videoObj.titleLoadFn(data);
			}
		}
		videoObj.loadedLinksCount++;
		if (num % 30 === 0) {
			return Promise.reject(new TasksPool.PauseError(3000));
		}
		return sleep(250);
	};
	Videos._getYTInfoAPI = function (info, num, id) {
		return $ajax("https://www.googleapis.com/youtube/v3/videos?key=" + Cfg.ytApiKey + "&id=" + id + "&part=snippet,statistics&fields=items/snippet/title,items/snippet/publishedAt,items/snippet/channelTitle,items/statistics/viewCount", null, false).then(function (xhr) {
			var items = JSON.parse(xhr.responseText).items[0];
			return Videos._titlesLoaderHelper(info, num, items.snippet.title, items.snippet.channelTitle, items.statistics.viewCount, items.snippet.publishedAt.substr(0, 10));
		})["catch"](function () {
			return Videos._getYTInfoOembed(info, num, id);
		});
	};
	Videos._getYTInfoOembed = function (info, num, id) {
		return $ajax("http://www.youtube.com/oembed?url=http%3A//youtube.com/watch%3Fv%3D" + id + "&format=json", null, false).then(function (xhr) {
			var json = JSON.parse(xhr.responseText);
			return Videos._titlesLoaderHelper(info, num, json.title, json.author_name, null, null);
		})["catch"](function () {
			return Videos._titlesLoaderHelper(info, num);
		});
	};
	Videos._getTitlesLoader = function () {
		return Cfg.YTubeTitles && new TasksPool(4, function (num, info) {
			var _info = _slicedToArray(info, 4);

			var isYtube = _info[1];
			var id = _info[3];

			if (isYtube) {
				return Cfg.ytApiKey ? Videos._getYTInfoAPI(info, num, id) : Videos._getYTInfoOembed(info, num, id);
			}
			return $ajax(aib.prot + "//vimeo.com/api/v2/video/" + id + ".json", null, false).then(function (xhr) {
				var entry = JSON.parse(xhr.responseText)[0];
				return Videos._titlesLoaderHelper(info, num, entry.title, entry.user_name, entry.stats_number_of_plays, /(.*)\s(.*)?/.exec(entry.upload_date)[1]);
			})["catch"](function () {
				return Videos._titlesLoaderHelper(info, num);
			});
		}, function () {
			sesStorage["de-videos-data1"] = JSON.stringify(Videos._global.vData);
		});
	};
	Videos.prototype = Object.defineProperties({
		currentLink: null,
		hasLinks: false,
		playerInfo: null,
		titleLoadFn: null,
		linksCount: 0,
		loadedLinksCount: 0,
		addLink: function addLink(m, loader, link, isYtube) {
			var time, dataObj;
			this.hasLinks = true;
			this.linksCount++;
			if (this.playerInfo === null) {
				if (Cfg.addYouTube === 2) {
					this.addPlayer(m, isYtube);
				} else if (Cfg.addYouTube > 2) {
					this._addThumb(m, isYtube);
				}
			} else if (!link && $q(".de-video-link[href*=\"" + m[1] + "\"]", this.post.msg)) {
				return;
			}
			if (loader && (dataObj = Videos._global.vData[isYtube ? 0 : 1][m[1]])) {
				this.vData[isYtube ? 0 : 1].push(dataObj);
			}
			if (m[4] || m[3] || m[2]) {
				if (m[4] >= 60) {
					m[3] = (m[3] || 0) + Math.floor(m[4] / 60);
					m[4] %= 60;
				}
				if (m[3] >= 60) {
					m[2] = (m[2] || 0) + Math.floor(m[3] / 60);
					m[3] %= 60;
				}
				time = (m[2] ? m[2] + "h" : "") + (m[3] ? m[3] + "m" : "") + (m[4] ? m[4] + "s" : "");
			}
			if (link) {
				link.href = link.href.replace(/^http:/, "https:");
				if (time) {
					link.setAttribute("de-time", time);
				}
				link.className = "de-video-link " + (isYtube ? "de-ytube" : "de-vimeo");
				if (dataObj) {
					link.textContent = dataObj[0];
					link.classList.add("de-video-title");
					link.setAttribute("de-author", dataObj[1]);
					link.title = Lng.author[lang] + dataObj[1] + (dataObj[2] ? ", " + Lng.views[lang] + dataObj[2] : "") + (dataObj[3] ? ", " + Lng.published[lang] + dataObj[3] : "");
				}
			} else {
				var src = isYtube ? aib.prot + "//www.youtube.com/watch?v=" + m[1] + (time ? "#t=" + time : "") : aib.prot + "//vimeo.com/" + m[1];
				this.post.msg.insertAdjacentHTML("beforeend", "<p class=\"de-video-ext\"><a class=\"de-video-link " + (isYtube ? "de-ytube" : "de-vimeo") + (dataObj ? " de-video-title\" title=\"" + Lng.author[lang] + dataObj[1] + ", " + Lng.views[lang] + dataObj[2] + ", " + Lng.published[lang] + dataObj[3] + "\" de-author=\"" + dataObj[1] : "") + (time ? "\" de-time=\"" + time : "") + "\" href=\"" + src + "\">" + (dataObj ? dataObj[0] : src) + "</a></p>");
				link = this.post.msg.lastChild.firstChild;
			}
			if (this.playerInfo === null || this.playerInfo === m) {
				this.currentLink = link;
			}
			link.videoInfo = m;
			if (loader && !dataObj) {
				loader.run([link, isYtube, this, m[1]]);
			}
		},
		addPlayer: function addPlayer(m, isYtube) {
			this.playerInfo = m;
			Videos.addPlayer(this.player, m, isYtube);
		},
		clickLink: function clickLink(el, mode) {
			var m = el.videoInfo;
			if (this.playerInfo !== m) {
				this.currentLink.classList.remove("de-current");
				this.currentLink = el;
				if (mode > 2) {
					this._addThumb(m, el.classList.contains("de-ytube"));
				} else {
					el.classList.add("de-current");
					this.addPlayer(m, el.classList.contains("de-ytube"));
				}
				return;
			}
			if (mode === 3) {
				if ($c("de-video-thumb", this.player)) {
					el.classList.add("de-current");
					this.addPlayer(m, el.classList.contains("de-ytube"));
				} else {
					el.classList.remove("de-current");
					this._addThumb(m, el.classList.contains("de-ytube"));
				}
			} else {
				el.classList.remove("de-current");
				this.player.innerHTML = "";
				this.playerInfo = null;
			}
		},
		updatePost: function updatePost(oldLinks, newLinks, cloned) {
			var loader = !cloned && Videos._getTitlesLoader();
			for (var i = 0, j = 0, len = newLinks.length; i < len; ++i) {
				var el = newLinks[i],
				    link = oldLinks[j];
				if (link && link.classList.contains("de-current")) {
					this.currentLink = el;
				}
				if (cloned) {
					el.videoInfo = link.videoInfo;
					j++;
				} else {
					var m = el.href.match(Videos.ytReg);
					if (m) {
						this.addLink(m, loader, el, true);
						j++;
					}
				}
			}
			this.currentLink = this.currentLink || newLinks[0];
			if (loader) {
				loader.complete();
			}
		},

		_addThumb: function _addThumb(m, isYtube) {
			var el = this.player,
			    wh = " width=\"" + Cfg.YTubeWidth + "\" height=\"" + Cfg.YTubeHeigh + "\"></a>";
			this.playerInfo = m;
			if (isYtube) {
				el.innerHTML = "<a href=\"" + aib.prot + "//www.youtube.com/watch?v=" + m[1] + "\" target=\"_blank\">" + "<img class=\"de-video-thumb de-ytube\" src=\"https://i.ytimg.com/vi/" + m[1] + "/0.jpg\"" + wh;
				return;
			}
			el.innerHTML = "<a href=\"" + aib.prot + "//vimeo.com/" + m[1] + "\" target=\"_blank\">" + "<img class=\"de-video-thumb de-vimeo\" src=\"\"" + wh;
			$ajax(aib.prot + "//vimeo.com/api/v2/video/" + m[1] + ".json", null, false).then(function (xhr) {
				try {
					el.firstChild.firstChild.setAttribute("src", JSON.parse(xhr.responseText)[0].thumbnail_large);
				} catch (e) {}
			});
		}
	}, {
		player: {
			get: function () {
				var val = aib.insertYtPlayer(this.post.msg, "<div class=\"de-video-obj\"></div>");
				Object.defineProperty(this, "player", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		}
	});

	function VideosParser() {
		this._loader = Videos._getTitlesLoader();
	}
	VideosParser.prototype = {
		end: function end() {
			if (this._loader) {
				this._loader.complete();
			}
		},
		parse: function parse() {
			var post = arguments[0] === undefined ? null : arguments[0];

			var loader = this._loader,
			    vids = aib.fixVideo(post),
			    links = $Q("a[href*=\"youtu\"]", post ? post.el : dForm.el);
			for (var i = 0, len = links.length; i < len; ++i) {
				var link = links[i],
				    m = link.href.match(Videos.ytReg);
				if (m) {
					var mPost = post || (aib.getPostEl(link) || {}).post;
					if (mPost) {
						mPost.videos.addLink(m, loader, link, true);
					}
				}
			}
			if (Cfg.addVimeo) {
				links = $Q("a[href*=\"vimeo.com\"]", post ? post.el : dForm.el);
				for (var i = 0, len = links.length; i < len; ++i) {
					var link = links[i],
					    m = link.href.match(Videos.vimReg);
					if (m) {
						var mPost = post || (aib.getPostEl(link) || {}).post;
						if (mPost) {
							mPost.videos.addLink(m, loader, link, false);
						}
					}
				}
			}
			for (var i = 0, len = vids.length; i < len; ++i) {
				var _vids$i = _slicedToArray(vids[i], 3);

				var pst = _vids$i[0];
				var m = _vids$i[1];
				var isYtube = _vids$i[2];

				if (pst) {
					pst.videos.addLink(m, loader, null, isYtube);
				}
			}
			return this;
		}
	};

	function embedMediaLinks(post) {
		if (Cfg.addMP3) {
			var els = $Q("a[href*=\".mp3\"]", post ? post.el : dForm.el);
			for (var i = 0, len = els.length; i < len; ++i) {
				var link = els[i];
				if (link.target !== "_blank" && link.rel !== "nofollow") {
					continue;
				}
				var src = link.href,
				    el = (post || aib.getPostEl(link).post).mp3Obj;
				if (nav.canPlayMP3) {
					if (!$q("audio[src=\"" + src + "\"]", el)) {
						el.insertAdjacentHTML("beforeend", "<p><audio src=\"" + src + "\" preload=\"none\" controls></audio></p>");
						link = el.lastChild.firstChild;
						link.addEventListener("play", updater.addPlayingTag, false);
						link.addEventListener("pause", updater.removePlayingTag, false);
					}
				} else if (!$q("object[FlashVars*=\"" + src + "\"]", el)) {
					el.insertAdjacentHTML("beforeend", "<object data=\"http://junglebook2007.narod.ru/audio/player.swf\" type=\"application/x-shockwave-flash\" wmode=\"transparent\" width=\"220\" height=\"16\" FlashVars=\"playerID=1&amp;bg=0x808080&amp;leftbg=0xB3B3B3&amp;lefticon=0x000000&amp;rightbg=0x808080&amp;rightbghover=0x999999&amp;rightcon=0x000000&amp;righticonhover=0xffffff&amp;text=0xffffff&amp;slider=0x222222&amp;track=0xf5f5dc&amp;border=0x666666&amp;loader=0x7fc7ff&amp;loop=yes&amp;autostart=no&amp;soundFile=" + src + "\"><br>");
				}
			}
		}
		if (Cfg.addVocaroo) {
			var els = $Q("a[href*=\"vocaroo.com\"]", post ? post.el : dForm.el);
			for (var i = 0, len = els.length; i < len; ++i) {
				var link = els[i],
				    src = link.href.split("/").pop(),
				    el = link.previousSibling;
				if (!el || el.className !== "de-vocaroo") {
					link.insertAdjacentHTML("beforebegin", "<div class=\"de-vocaroo\"><embed" + " width=\"148\" height=\"44\" wmode=\"transparent\" type=\"application/x-shockwave-flash\"" + " src=\"http://vocaroo.com/player.swf?playMediaID=" + src + "\"></div>");
				}
			}
		}
	}




	function AjaxError(code, message) {
		this.name = "AjaxError";
		this.code = code;
		this.message = message;
	}
	AjaxError.Success = new AjaxError(200, "");

	function ajaxLoad(url) {
		var returnForm = arguments[1] === undefined ? true : arguments[1];
		var useCache = arguments[2] === undefined ? false : arguments[2];

		return $ajax(url, { useCache: useCache }).then(function (xhr) {
			var el,
			    text = xhr.responseText;
			if ((aib.futa ? /<!--gz-->$/ : /<\/html?>[\s\n\r]*$/).test(text)) {
				el = returnForm ? $q(aib.qDForm, $DOM(text)) : $DOM(text);
			}
			return el ? el : Promise.reject(new AjaxError(0, Lng.errCorruptData[lang]));
		}, function (xhr) {
			return xhr.status === 304 ? null : Promise.reject(new AjaxError(xhr.status, xhr.statusText));
		});
	}

	function getJsonPosts(url) {
		return $ajax(url, { useCache: true }).then(function (xhr) {
			return JSON.parse(xhr.responseText);
		}, function (xhr) {
			return xhr.status === 304 ? null : Promise.reject(new AjaxError(xhr.status, xhr.statusText));
		});
	}

	var loadPages = async(regeneratorRuntime.mark(function callee$1$1(count) {
		var hasError, i, len, content;
		return regeneratorRuntime.wrap(function callee$1$1$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					hasError = false;

					$alert(Lng.loading[lang], "load-pages", true);
					Pview.clearCache();
					isExpImg = false;
					pByNum = Object.create(null);
					Post.hiddenNums = [];
					if (Attachment.viewer) {
						Attachment.viewer.close(null);
						Attachment.viewer = null;
					}
					dForm.hide();
					dForm.clear();
					if (pr.isQuick) {
						if (pr.file) {
							pr.delFilesUtils();
						}
						pr.txta.value = "";
					}
					i = aib.page, len = Math.min(aib.lastPage + 1, aib.page + count);

				case 11:
					if (!(i < len)) {
						context$2$0.next = 35;
						break;
					}

					context$2$0.prev = 12;
					context$2$0.next = 15;
					return ajaxLoad(aib.getPageUrl(aib.b, i));

				case 15:
					context$2$0.t15 = context$2$0.sent;
					content = replacePost(context$2$0.t15);
					context$2$0.next = 22;
					break;

				case 19:
					context$2$0.prev = 19;
					context$2$0.t16 = context$2$0["catch"](12);

					content = $add("<div><center style=\"font-size: 2em\">" + getErrorMessage(context$2$0.t16) + "</center><hr></div>");

				case 22:
					if (i != aib.page) {
						dForm.el.insertAdjacentHTML("beforeend", "<center style=\"font-size: 2em\">" + Lng.page[lang] + " " + i + "</center><hr>");
					}
					context$2$0.prev = 23;

					dForm.addFormContent(content, true);
					context$2$0.next = 32;
					break;

				case 27:
					context$2$0.prev = 27;
					context$2$0.t17 = context$2$0["catch"](23);

					$alert(getPrettyErrorMessage(context$2$0.t17), "load-pages", true);
					hasError = true;
					return context$2$0.abrupt("break", 35);

				case 32:
					++i;
					context$2$0.next = 11;
					break;

				case 35:
					if (hasError) {
						context$2$0.next = 43;
						break;
					}

					dForm.initAjax();
					addDelformStuff();
					return context$2$0.delegateYield(readUserPosts(), "t18", 39);

				case 39:
					return context$2$0.delegateYield(readFavoritesPosts(), "t19", 40);

				case 40:
					$each($Q("input[type=\"password\"]", dForm.el), function (pEl) {
						pr.dpass = pEl;
						pEl.value = Cfg.passwValue;
					});
					if (hKeys) {
						hKeys.clear(aib.page + count - 1);
					}
					closeAlert($id("de-alert-load-pages"));

				case 43:
					dForm.show();

				case 44:
				case "end":
					return context$2$0.stop();
			}
		}, callee$1$1, this, [[12, 19], [23, 27]]);
	}));

	function infoLoadErrors(e) {
		var showError = arguments[1] === undefined ? true : arguments[1];

		var isAjax = e instanceof AjaxError,
		    eCode = isAjax ? e.code : 0;
		if (eCode === 200) {
			closeAlert($id("de-alert-newposts"));
		} else if (isAjax && eCode === 0) {
			$alert(e.message || Lng.noConnect[lang], "newposts", false);
		} else {
			$alert(Lng.thrNotFound[lang] + aib.t + "): \n" + getErrorMessage(e), "newposts", false);
			if (showError) {
				doc.title = "{" + eCode + "} " + doc.title;
			}
		}
	}




	function Spells(read) {
		if (read) {
			this._read(true);
		} else {
			this.disable(false);
		}
	}
	Spells.names = ["words", "exp", "exph", "imgn", "ihash", "subj", "name", "trip", "img", "sage", "op", "tlen", "all", "video", "wipe", "num", "vauthor"];
	Spells.needArg = [
	true, true, true, true, true,
	false, true, false, false, false,
	false, false, false, false, false,
	true, true];
	Spells.decompileSpell = function (type, neg, val, scope) {
		var wipeMsg = arguments[4] === undefined ? null : arguments[4];

		var spell = (neg ? "!#" : "#") + Spells.names[type] + (scope ? "[" + scope[0] + (scope[1] ? "," + (scope[1] === -1 ? "" : scope[1]) : "") + "]" : "");
		if (!val) {
			return spell;
		}
	
		if (type === 8) {
			return spell + "(" + (val[0] === 2 ? ">" : val[0] === 1 ? "<" : "=") + (val[1] ? val[1][0] + (val[1][1] === val[1][0] ? "" : "-" + val[1][1]) : "") + (val[2] ? "@" + val[2][0] + (val[2][0] === val[2][1] ? "" : "-" + val[2][1]) + "x" + val[2][2] + (val[2][2] === val[2][3] ? "" : "-" + val[2][3]) : "") + ")";
		}
	
		else if (type === 14) {
			if (val === 63 && !wipeMsg) {
				return spell;
			}

			var _ref = wipeMsg || [];

			var _ref2 = _slicedToArray(_ref, 2);

			var msgBit = _ref2[0];
			var msgData = _ref2[1];
			var names = [];
			var bits = { 1: "samelines", 2: "samewords", 4: "longwords", 8: "symbols",
				16: "capslock", 32: "numbers", 64: "whitespace"
			};
			for (var bit in bits) {
				if (+bit !== msgBit) {
					if (val & +bit) {
						names.push(bits[bit]);
					}
				}
			}
			if (msgBit) {
				names.push(bits[msgBit].toUpperCase() + (msgData ? ": " + msgData : ""));
			}
			return spell + "(" + names.join(",") + ")";
		}
	
		else if (type === 15 || type === 11) {
			var temp_,
			    temp = val[1].length - 1;
			if (temp !== -1) {
				for (temp_ = []; temp >= 0; --temp) {
					temp_.push(val[1][temp][0] + "-" + val[1][temp][1]);
				}
				temp_.reverse();
			}
			spell += "(";
			if (val[0].length) {
				spell += val[0].join(",") + (temp_ ? "," : "");
			}
			if (temp_) {
				spell += temp_.join(",");
			}
			return spell + ")";
		}
	
		else if (type === 0 || type === 6 || type === 7 || type === 16) {
			return spell + "(" + val.replace(/\)/g, "\\)") + ")";
		} else {
			return spell + "(" + String(val) + ")";
		}
	};
	Spells.prototype = Object.defineProperties({
		_optimizeSpells: function _optimizeSpells(spells) {
			var neg,
			    lastSpell = -1,
			    newSpells = [];
			for (var i = 0, len = spells.length; i < len; ++i) {
				var j,
				    spell = spells[i],
				    flags = spell[0],
				    type = flags & 255;
				neg = (flags & 256) !== 0;
				if (type === 255) {
					var parensSpells = this._optimizeSpells(spell[1]);
					if (parensSpells) {
						if (parensSpells.length !== 1) {
							newSpells.push([flags, parensSpells]);
							lastSpell++;
							continue;
						} else if ((parensSpells[0][0] & 255) !== 12) {
							newSpells.push([(parensSpells[0][0] | flags & 512) ^ flags & 256, parensSpells[0][1]]);
							lastSpell++;
							continue;
						}
						flags = parensSpells[0][0];
						neg = !(neg ^ (flags & 256) !== 0);
					}
				} else {
					var scope = spell[2];
					if (!scope || scope[0] === aib.b && (scope[1] === -1 ? !aib.t : !scope[1] || scope[1] === aib.t)) {
						if (type === 12) {
							neg = !neg;
						} else {
							newSpells.push([flags, spell[1]]);
							lastSpell++;
							continue;
						}
					}
				}
				for (j = lastSpell; j >= 0 && (newSpells[j][0] & 512) !== 0 ^ neg; --j) {}
				if (j !== lastSpell) {
					newSpells = newSpells.slice(0, j + 1);
					lastSpell = j;
				}
				if (neg && j !== -1) {
					newSpells[j][0] &= 511;
				}
				if ((flags & 512) !== 0 ^ neg) {
					break;
				}
			}
			return lastSpell === -1 ? neg ? [[12, ""]] : null : newSpells;
		},
		_initSpells: function _initSpells(data) {
			if (data) {
				data.forEach(function initExps(item) {
					var val = item[1];
					if (val) {
						switch (item[0] & 255) {
							case 1:
							case 2:
							case 3:
							case 5:
							case 13:
								item[1] = toRegExp(val, true);break;
							case 255:
								val.forEach(initExps);
						}
					}
				});
			}
			return data;
		},
		_decompileScope: function _decompileScope(scope, indent) {
			var dScope = [],
			    hScope = false;
			for (var i = 0, j = 0, len = scope.length; i < len; i++, j++) {
				var spell = scope[i],
				    type = spell[0] & 255;
				if (type === 255) {
					hScope = true;
					var temp = this._decompileScope(spell[1], indent + "    ");
					if (temp[1]) {
						var str = (spell[0] & 256 ? "!(\n" : "(\n") + indent + "    " + temp[0].join("\n" + indent + "    ") + "\n" + indent + ")";
						if (j === 0) {
							dScope[0] = str;
						} else {
							dScope[--j] += " " + str;
						}
					} else {
						dScope[j] = (spell[0] & 256 ? "!(" : "(") + temp[0].join(" ") + ")";
					}
				} else {
					dScope[j] = Spells.decompileSpell(type, spell[0] & 256, spell[1], spell[2]);
				}
				if (i !== len - 1) {
					dScope[j] += spell[0] & 512 ? " &" : " |";
				}
			}
			return [dScope, dScope.length > 2 || hScope];
		},
		_decompileSpells: function _decompileSpells() {
			var _this = this;

			var str,
			    reps,
			    oreps,
			    data = this._data;
			if (!data) {
				this._read(false);
				data = this._data;
				if (!data) {
					return this._list = "";
				}
			}
			str = data[1] ? this._decompileScope(data[1], "")[0].join("\n") : "";
			reps = data[2];
			oreps = data[3];
			if (reps || oreps) {
				if (str) {
					str += "\n\n";
				}
				if (reps) {
					reps.forEach(function (rep) {
						str += _this._decompileRep(rep, false) + "\n";
					});
				}
				if (oreps) {
					oreps.forEach(function (orep) {
						str += _this._decompileRep(orep, true) + "\n";
					});
				}
				str = str.substr(0, str.length - 1);
			}
			this._data = null;
			return this._list = str;
		},
		_decompileRep: function _decompileRep(rep, isOrep) {
			return (isOrep ? "#outrep" : "#rep") + (rep[0] ? "[" + rep[0] + (rep[1] ? "," + (rep[1] === -1 ? "" : rep[1]) : "") + "]" : "") + "(" + rep[2] + "," + rep[3].replace(/\)/g, "\\)") + ")";
		},
		_optimizeReps: function _optimizeReps(data) {
			if (!data) {
				return false;
			}
			var nData = [];
			data.forEach(function (temp) {
				if (!temp[0] || temp[0] === aib.b && (temp[1] === -1 ? !aib.t : !temp[1] || temp[1] === aib.t)) {
					nData.push([temp[2], temp[3]]);
				}
			});
			return !nData.length ? false : nData;
		},
		_initReps: function _initReps(data) {
			if (data) {
				for (var i = data.length - 1; i >= 0; i--) {
					data[i][0] = toRegExp(data[i][0], false);
				}
			}
			return data;
		},
		_init: function _init(spells, reps, outreps) {
			this._spells = this._initSpells(spells);
			this._reps = this._initReps(reps);
			this._outreps = this._initReps(outreps);
			this.enable = !!this._spells;
			this.haveReps = !!reps;
			this.haveOutreps = !!outreps;
		},
		_read: function _read(init) {
			var spells, data;
			try {
				spells = JSON.parse(Cfg.spells);
				data = JSON.parse(sesStorage["de-spells-" + aib.b + (aib.t || "")]);
			} catch (e) {}
			if (data && spells && data[0] === spells[0]) {
				this._data = spells;
				if (init) {
					this.hash = data[0];
					this._init(data[1], data[2], data[3]);
				}
				return;
			}
			if (!spells) {
				spells = this.parseText("#wipe(samelines,samewords,longwords,symbols,numbers,whitespace)");
			}
			if (init) {
				this.update(spells, false, false);
			} else {
				this._data = spells;
			}
		},
		_data: null,
		_list: "",

		hash: 0,
		hasNumSpell: false,
		enable: false,
		parseText: function parseText(str) {
			var codeGen = new SpellsCodegen(str),
			    data = codeGen.generate();
			if (codeGen.hasError) {
				$alert(Lng.error[lang] + ": " + codeGen.error, "help-err-spell", false);
			} else if (data) {
				if (data[0] && Cfg.sortSpells) {
					this.sort(data[0]);
				}
				return [Date.now(), data[0], data[1], data[2]];
			}
			return null;
		},
		sort: function sort(sp) {
		
			for (var i = 0, len = sp.length - 1; i < len; i++) {
				if (sp[i][0] > 512) {
					var temp = [255, []];
					do {
						temp[1].push(sp.splice(i, 1)[0]);
						len--;
					} while (sp[i][0] > 512);
					temp[1].push(sp.splice(i, 1)[0]);
					sp.splice(i, 0, temp);
				}
			}
			sp = sp.sort();
			for (var i = 0, len = sp.length - 1; i < len; i++) {
			
				if (sp[i][0] === sp[i + 1][0] && sp[i][1] <= sp[i + 1][1] && sp[i][1] >= sp[i + 1][1] && (sp[i][2] === null ||
				sp[i][2] === undefined || sp[i][2] <= sp[i + 1][2] && sp[i][2] >= sp[i + 1][2])) {
				
					sp.splice(i + 1, 1);
					i--;
					len--;
				
				} else if (sp[i][0] === 255) {
					sp.push(sp.splice(i, 1)[0]);
					i--;
					len--;
				}
			}
		},
		update: function update(data, sync, isHide) {
			var spells = data[1] ? this._optimizeSpells(data[1]) : false,
			    reps = this._optimizeReps(data[2]),
			    outreps = this._optimizeReps(data[3]);
			saveCfg("spells", JSON.stringify(data));
			sesStorage["de-spells-" + aib.b + (aib.t || "")] = JSON.stringify([data[0], spells, reps, outreps]);
			this._data = data;
			this._list = "";
			this.hash = data[0];
			if (sync) {
				locStorage["__de-spells"] = JSON.stringify({
					hide: !!this.list && !!isHide,
					data: data
				});
				locStorage.removeItem("__de-spells");
			}
			this._init(spells, reps, outreps);
		},
		setSpells: function setSpells(spells, sync) {
			this.update(spells, sync, Cfg.hideBySpell);
			if (Cfg.hideBySpell) {
				var sRunner = new SpellsRunner();
				for (var post = dForm.firstThr.op; post; post = post.next) {
					sRunner.run(post);
				}
				sRunner.end();
			} else {
				this.enable = false;
			}
		},
		disable: function disable(sync) {
			this.enable = false;
			this._list = "";
			this._data = null;
			this.haveReps = this.haveOutreps = false;
			saveCfg("hideBySpell", false);
		},
		replace: function replace(txt) {
			for (var i = 0, len = this._reps.length; i < len; i++) {
				txt = txt.replace(this._reps[i][0], this._reps[i][1]);
			}
			return txt;
		},
		outReplace: function outReplace(txt) {
			for (var i = 0, len = this._outreps.length; i < len; i++) {
				txt = txt.replace(this._outreps[i][0], this._outreps[i][1]);
			}
			return txt;
		},
		addSpell: function addSpell(type, arg, scope, isNeg, spells) {
			if (!spells) {
				if (!this._data) {
					this._read(false);
				}
				spells = this._data || [Date.now(), [], false, false];
			}
			var idx,
			    sScope = String(scope),
			    sArg = String(arg);
			if (spells[1]) {
				spells[1].some(scope && isNeg ? function (spell, i) {
					var data;
					if (spell[0] === 255 && (data = spell[1]) instanceof Array && data.length === 2 && data[0][0] === 524 && data[1][0] === type && data[1][2] == null && String(data[1][1]) === sArg && String(data[0][2]) === sScope) {
						idx = i;
						return true;
					}
					return (spell[0] & 512) !== 0;
				} : function (spell, i) {
					if (spell[0] === type && String(spell[1]) === sArg && String(spell[2]) === sScope) {
						idx = i;
						return true;
					}
					return (spell[0] & 512) !== 0;
				});
			} else {
				spells[1] = [];
			}
			if (typeof idx !== "undefined") {
				spells[1].splice(idx, 1);
			} else if (scope && isNeg) {
				spells[1].splice(0, 0, [255, [[524, "", scope], [type, arg, void 0]], void 0]);
			} else {
				spells[1].splice(0, 0, [type, arg, scope]);
			}
			this.update(spells, true, true);
			idx = null;
		}
	}, {
		list: {
			get: function () {
				return this._list || this._decompileSpells();
			},
			configurable: true,
			enumerable: true
		}
	});

	function SpellsCodegen(sList) {
		this._line = 1;
		this._col = 1;
		this._sList = sList;
		this.hasError = false;
	}
	SpellsCodegen.prototype = Object.defineProperties({
		TYPE_UNKNOWN: 0,
		TYPE_ANDOR: 1,
		TYPE_NOT: 2,
		TYPE_SPELL: 3,
		TYPE_PARENTHESES: 4,
		TYPE_REPLACER: 5,

		generate: function generate() {
			return this._sList ? this._generate(this._sList, false) : null;
		},

		_errMsg: "",
		_errMsgArg: null,
		_generate: function _generate(sList, inParens) {
			var spells = [],
			    reps = [],
			    outreps = [],
			    lastType = this.TYPE_UNKNOWN,
			    hasReps = false;
			for (var i = 0, len = sList.length; i < len; i++, this._col++) {
				var res;
				switch (sList[i]) {
					case "\n":
						this._line++;
						this._col = 0;
										case "\r":
					case " ":
						continue;
					case "#":
						var name = "";
						i++;
						this._col++;
						while (sList[i] >= "a" && sList[i] <= "z" || sList[i] >= "A" && sList[i] <= "Z") {
							name += sList[i].toLowerCase();
							i++;
							this._col++;
						}
						if (name === "rep" || name === "outrep") {
							if (!hasReps) {
								if (inParens) {
									this._col -= 1 + name.length;
									this._setError(Lng.seRepsInParens[lang], "#" + name);
									return null;
								}
								if (lastType === this.TYPE_ANDOR || lastType === this.TYPE_NOT) {
									i -= 1 + name.length;
									this._col -= 1 + name.length;
									lookBack: while (i >= 0) {
										switch (sList[i]) {
											case "\n":
											case "\r":
											case " ":
												i--;
												this._col--;
												break;
											default:
												break lookBack;
										}
									}
									this._setError(Lng.seOpInReps[lang], sList[i]);
									return null;
								}
								hasReps = true;
							}
							res = this._doRep(name, sList.substr(i));
							if (!res) {
								return null;
							}
							if (name === "rep") {
								reps.push(res[1]);
							} else {
								outreps.push(res[1]);
							}
							i += res[0] - 1;
							this._col += res[0] - 1;
							lastType = this.TYPE_REPLACER;
						} else {
							if (lastType === this.TYPE_SPELL || lastType === this.TYPE_PARENTHESES) {
								this._setError(Lng.seMissOp[lang], null);
								return null;
							}
							res = this._doSpell(name, sList.substr(i), lastType === this.TYPE_NOT);
							if (!res) {
								return null;
							}
							i += res[0] - 1;
							this._col += res[0] - 1;
							spells.push(res[1]);
							lastType = this.TYPE_SPELL;
						}
						break;
					case "(":
						if (hasReps) {
							this._setError(Lng.seUnexpChar[lang], "(");
							return null;
						}
						if (lastType === this.TYPE_SPELL || lastType === this.TYPE_PARENTHESES) {
							this._setError(Lng.seMissOp[lang], null);
							return null;
						}
						res = this._generate(sList.substr(i + 1), true);
						if (!res) {
							return null;
						}
						i += res[0] + 1;
						spells.push([lastType === this.TYPE_NOT ? 511 : 255, res[1]]);
						lastType = this.TYPE_PARENTHESES;
						break;
					case "|":
					case "&":
						if (hasReps) {
							this._setError(Lng.seUnexpChar[lang], sList[i]);
							return null;
						}
						if (lastType !== this.TYPE_SPELL && lastType !== this.TYPE_PARENTHESES) {
							this._setError(Lng.seMissSpell[lang], null);
							return null;
						}
						if (sList[i] === "&") {
							spells[spells.length - 1][0] |= 512;
						}
						lastType = this.TYPE_ANDOR;
						break;
					case "!":
						if (hasReps) {
							this._setError(Lng.seUnexpChar[lang], "!");
							return null;
						}
						if (lastType !== this.TYPE_ANDOR && lastType !== this.TYPE_UNKNOWN) {
							this._setError(Lng.seMissOp[lang], null);
							return null;
						}
						lastType = this.TYPE_NOT;
						break;
					case ")":
						if (hasReps) {
							this._setError(Lng.seUnexpChar[lang], ")");
							return null;
						}
						if (lastType === this.TYPE_ANDOR || lastType === this.TYPE_NOT) {
							this._setError(Lng.seMissSpell[lang], null);
							return null;
						}
						if (inParens) {
							return [i, spells];
						}
										default:
						this._setError(Lng.seUnexpChar[lang], sList[i]);
						return null;
				}
			}
			if (inParens) {
				this._setError(Lng.seMissClBkt[lang], null);
				return null;
			}
			if (lastType !== this.TYPE_SPELL && lastType !== this.TYPE_PARENTHESES && lastType !== this.TYPE_REPLACER) {
				this._setError(Lng.seMissSpell[lang], null);
				return null;
			}
			if (!reps.length) {
				reps = false;
			}
			if (!outreps.length) {
				outreps = false;
			}
			return [spells, reps, outreps];
		},
		_getScope: function _getScope(str) {
			var m = str.match(/^\[([a-z0-9\/]+)(?:(,)|,(\s*[0-9]+))?\]/);
			if (m) {
				return [m[0].length, [m[1], m[3] ? m[3] : m[2] ? -1 : false]];
			}
			return null;
		},
		_getRegex: function _getRegex(str, haveComma) {
			var val,
			    m = str.match(/^\((\/.*?[^\\]\/[igm]*)(?:\)|\s*(,))/);
			if (!m) {
				return null;
			}
			if (haveComma !== !!m[2]) {
				return null;
			}
			val = m[1];
			try {
				toRegExp(val, true);
			} catch (e) {
				this._setError(Lng.seErrRegex[lang], val);
				return null;
			}
			return [m[0].length, val];
		},
		_getText: function _getText(str, haveBracket) {
			var m = str.match(/^(\()?(.*?[^\\])\)/);
			if (!m) {
				return null;
			}
			if (haveBracket !== !!m[1]) {
				return null;
			}
			return [m[0].length, m[2].replace(/\\\)/g, ")")];
		},
		_doRep: function _doRep(name, str) {
			var regex,
			    scope = this._getScope(str);
			if (scope) {
				str = str.substring(scope[0]);
			} else {
				scope = [0, ["", ""]];
			}
			regex = this._getRegex(str, true);
			if (regex) {
				str = str.substring(regex[0]);
				if (str[0] === ")") {
					return [regex[0] + scope[0] + 1, [scope[1][0], scope[1][1], regex[1], ""]];
				}
				var val = this._getText(str, false);
				if (val) {
					return [val[0] + regex[0] + scope[0], [scope[1][0], scope[1][1], regex[1], val[1]]];
				}
			}
			this._setError(Lng.seSyntaxErr[lang], name);
			return null;
		},
		_doSpell: function _doSpell(name, str, isNeg) {
			var m,
			    spellType,
			    val,
			    temp,
			    i = 0,
			    scope = null,
			    spellIdx = Spells.names.indexOf(name);
			if (spellIdx === -1) {
				this._setError(Lng.seUnknown[lang], name);
				return null;
			}
			temp = this._getScope(str);
			if (temp) {
				i += temp[0];
				str = str.substring(temp[0]);
				scope = temp[1];
			}
			spellType = isNeg ? spellIdx | 256 : spellIdx;
			if (str[0] !== "(" || str[1] === ")") {
				if (Spells.needArg[spellIdx]) {
					this._setError(Lng.seMissArg[lang], name);
					return null;
				}
				return [str[0] === "(" ? i + 2 : i, [spellType, spellIdx === 14 ? 63 : "", scope]];
			}
			switch (spellIdx) {
			
				case 4:
					m = str.match(/^\((\d+)\)/);
					if (+m[1] === +m[1]) {
						return [i + m[0].length, [spellType, +m[1], scope]];
					}
					break;
			
				case 8:
					m = str.match(/^\(([><=])(?:(\d+(?:\.\d+)?)(?:-(\d+(?:\.\d+)?))?)?(?:@(\d+)(?:-(\d+))?x(\d+)(?:-(\d+))?)?\)/);
					if (m && (m[2] || m[4])) {
						return [i + m[0].length, [spellType, [m[1] === "=" ? 0 : m[1] === "<" ? 1 : 2, m[2] && [+m[2], m[3] ? +m[3] : +m[2]], m[4] && [+m[4], m[5] ? +m[5] : +m[4], +m[6], m[7] ? +m[7] : +m[6]]], scope]];
					}
					break;
			
				case 14:
					m = str.match(/^\(([a-z, ]+)\)/);
					if (m) {
						val = m[1].split(/, */).reduce(function (val, str) {
							switch (str) {
								case "samelines":
									return val |= 1;
								case "samewords":
									return val |= 2;
								case "longwords":
									return val |= 4;
								case "symbols":
									return val |= 8;
								case "capslock":
									return val |= 16;
								case "numbers":
									return val |= 32;
								case "whitespace":
									return val |= 64;
								default:
									return -1;
							}
						}, 0);
						if (val !== -1) {
							return [i + m[0].length, [spellType, val, scope]];
						}
					}
					break;
			
				case 11:
				case 15:
					m = str.match(/^\(([\d-, ]+)\)/);
					if (m) {
						m[1].split(/, */).forEach(function (v) {
							if (v.includes("-")) {
								var nums = v.split("-");
								nums[0] = +nums[0];
								nums[1] = +nums[1];
								this[1].push(nums);
							} else {
								this[0].push(+v);
							}
						}, val = [[], []]);
						return [i + m[0].length, [spellType, val, scope]];
					}
					break;
			
				case 1:
				case 2:
				case 3:
				case 5:
				case 13:
					temp = this._getRegex(str, false);
					if (temp) {
						return [i + temp[0], [spellType, temp[1], scope]];
					}
					break;
			
				default:
					temp = this._getText(str, true);
					if (temp) {
						return [i + temp[0], [spellType, spellIdx === 0 ? temp[1].toLowerCase() : temp[1], scope]];
					}
			}
			this._setError(Lng.seSyntaxErr[lang], name);
			return null;
		},
		_setError: function _setError(msg, arg) {
			this.hasError = true;
			this._errMsg = msg;
			this._errMsgArg = arg;
		}
	}, {
		error: {
			get: function () {
				if (!this.hasError) {
					return "";
				}
				return (this._errMsgArg ? this._errMsg.replace("%s", this._errMsgArg) : this._errMsg) + Lng.seRow[lang] + this._line + Lng.seCol[lang] + this._col + ")";
			},
			configurable: true,
			enumerable: true
		}
	});

	function SpellsRunner() {
		var savePosts = arguments[0] === undefined ? true : arguments[0];

		this._spells = spells._spells;
		if (!this._spells) {
			this.run = function () {
				return 0;
			};
			this._savePosts = false;
		}
		this._savePosts = savePosts;
	}
	SpellsRunner.prototype = {
		hasNumSpell: false,
		end: function end() {
			var _this = this;

			if (this._endPromise) {
				this._endPromise.then(function () {
					return _this._savePostsHelper();
				});
			} else {
				this._savePostsHelper();
			}
		},
		run: function run(post) {
			var _this = this;

			var interp = new SpellsInterpreter(post, this._spells);
			var res = interp.run();
			if (res instanceof Promise) {
				res = res.then(function (val) {
					return _this._checkRes(post, val);
				});
				this._endPromise = this._endPromise ? this._endPromise.then(function () {
					return res;
				}) : res;
				return 0;
			}
			return this._checkRes(post, res);
		},

		_endPromise: null,
		_checkRes: function _checkRes(post, _ref) {
			var _ref2 = _slicedToArray(_ref, 3);

			var hasNumSpell = _ref2[0];
			var val = _ref2[1];
			var msg = _ref2[2];

			this.hasNumSpell |= hasNumSpell;
			if (val) {
				post.spellHide(msg);
				return 1;
			}
			if (!post.deleted) {
				sVis[post.count] = 1;
			}
			return 0;
		},
		_savePostsHelper: function _savePostsHelper() {
			if (this._savePosts) {
				if (aib.t) {
					var lPost = dForm.firstThr.lastNotDeleted;
					sesStorage["de-hidden-" + aib.b + aib.t] = (Cfg.hideBySpell ? spells.hash : "0") + "," + lPost.num + "," + lPost.count + "," + sVis.join("");
				}
				saveHiddenThreads(false);
				toggleContent("hid", true);
			}
			ImagesHashStorage.endFn();
		}
	};

	function SpellsInterpreter(post, spells) {
		this._post = post;
		this._ctx = [spells.length, spells, 0, false];
		this._lastTSpells = [];
		this._triggeredSpellsStack = [this._lastTSpells];
		this._deep = 0;
	}
	SpellsInterpreter.prototype = {
		hasNumSpell: false,
		run: function run() {
			var rv,
			    stopCheck,
			    isNegScope = this._ctx.pop(),
			    i = this._ctx.pop(),
			    scope = this._ctx.pop(),
			    len = this._ctx.pop();
			while (true) {
				if (i < len) {
					var type = scope[i][0] & 255;
					if (type === 255) {
						this._deep++;
						this._ctx.push(len, scope, i, isNegScope);
						isNegScope = !!((scope[i][0] & 256) !== 0 ^ isNegScope);
						scope = scope[i][1];
						len = scope.length;
						i = 0;
						this._lastTSpells = [];
						this._triggeredSpellsStack.push(this._lastTSpells);
						continue;
					}
					var val = this._runSpell(type, scope[i][1]);
					if (val instanceof Promise) {
						this._ctx.push(len, scope, ++i, isNegScope);
						return val.then(this._asyncContinue.bind(this));
					}

					var _ref = this._checkRes(scope[i], val, isNegScope);

					var _ref2 = _slicedToArray(_ref, 2);

					rv = _ref2[0];
					stopCheck = _ref2[1];

					if (!stopCheck) {
						i++;
						continue;
					}
				}
				if (this._deep !== 0) {
					this._deep--;
					isNegScope = this._ctx.pop();
					i = this._ctx.pop();
					scope = this._ctx.pop();
					len = this._ctx.pop();
					if ((scope[i][0] & 512) === 0 ^ rv) {
						i++;
						this._triggeredSpellsStack.pop();
						this._lastTSpells = this._triggeredSpellsStack[this._triggeredSpellsStack.length - 1];
						continue;
					}
				}
				return [this.hasNumSpell, rv, rv ? this._getMsg() : null];
			}
		},

		_wipeMsg: null,
		_asyncContinue: function _asyncContinue(val) {
			var cl = this._ctx.length;
			var spell = this._ctx[cl - 3][this._ctx[cl - 2] - 1];

			var _checkRes = this._checkRes(spell, val, this._ctx[cl - 1]);

			var _checkRes2 = _slicedToArray(_checkRes, 2);

			var rv = _checkRes2[0];
			var stopCheck = _checkRes2[1];

			return stopCheck ? [this.hasNumSpell, rv, rv ? this._getMsg() : null] : this.run();
		},
		_checkRes: function _checkRes(spell, val, isNegScope) {
			var flags = spell[0];
			var isAndSpell = (flags & 512) !== 0 ^ isNegScope;
			var isNegSpell = (flags & 256) !== 0 ^ isNegScope;
			if (isNegSpell ^ val) {
				this._lastTSpells.push([isNegSpell, spell, (spell[0] & 255) === 14 ? this._wipeMsg : null]);
				return [true, !isAndSpell];
			}
			this._lastTSpells.length = 0;
			return [false, isAndSpell];
		},
		_getMsg: function _getMsg() {
			var rv = [];
			var _iteratorNormalCompletion = true;
			var _didIteratorError = false;
			var _iteratorError = undefined;

			try {
				for (var _iterator = this._triggeredSpellsStack[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {
					var spellEls = _step.value;
					var _iteratorNormalCompletion2 = true;
					var _didIteratorError2 = false;
					var _iteratorError2 = undefined;

					try {
						for (var _iterator2 = spellEls[Symbol.iterator](), _step2; !(_iteratorNormalCompletion2 = (_step2 = _iterator2.next()).done); _iteratorNormalCompletion2 = true) {
							var _step2$value = _slicedToArray(_step2.value, 3);

							var isNeg = _step2$value[0];
							var spell = _step2$value[1];
							var wipeMsg = _step2$value[2];

							rv.push(Spells.decompileSpell(spell[0] & 255, isNeg, spell[1], spell[2], wipeMsg));
						}
					} catch (err) {
						_didIteratorError2 = true;
						_iteratorError2 = err;
					} finally {
						try {
							if (!_iteratorNormalCompletion2 && _iterator2["return"]) {
								_iterator2["return"]();
							}
						} finally {
							if (_didIteratorError2) {
								throw _iteratorError2;
							}
						}
					}
				}
			} catch (err) {
				_didIteratorError = true;
				_iteratorError = err;
			} finally {
				try {
					if (!_iteratorNormalCompletion && _iterator["return"]) {
						_iterator["return"]();
					}
				} finally {
					if (_didIteratorError) {
						throw _iteratorError;
					}
				}
			}

			return rv.join(" & ");
		},
		_runSpell: function _runSpell(spellId, val) {
			switch (spellId) {
				case 0:
					return this._words(val);
				case 1:
					return this._exp(val);
				case 2:
					return this._exph(val);
				case 3:
					return this._imgn(val);
				case 4:
					return this._ihash(val);
				case 5:
					return this._subj(val);
				case 6:
					return this._name(val);
				case 7:
					return this._trip(val);
				case 8:
					return this._img(val);
				case 9:
					return this._sage(val);
				case 10:
					return this._op(val);
				case 11:
					return this._tlen(val);
				case 12:
					return this._all(val);
				case 13:
					return this._video(val);
				case 14:
					return this._wipe(val);
				case 15:
					this.hasNumSpell = true;
					return this._num(val);
				case 16:
					return this._vauthor(val);
			}
		},
		_words: function _words(val) {
			return this._post.text.toLowerCase().includes(val) || this._post.subj.toLowerCase().includes(val);
		},
		_exp: function _exp(val) {
			return val.test(this._post.text);
		},
		_exph: function _exph(val) {
			return val.test(this._post.html);
		},
		_imgn: function _imgn(val) {
			var _iteratorNormalCompletion = true;
			var _didIteratorError = false;
			var _iteratorError = undefined;

			try {
				for (var _iterator = this._post.images.data[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {
					var image = _step.value;

					if (image instanceof Attachment && val.test(image.info)) {
						return true;
					}
				}
			} catch (err) {
				_didIteratorError = true;
				_iteratorError = err;
			} finally {
				try {
					if (!_iteratorNormalCompletion && _iterator["return"]) {
						_iterator["return"]();
					}
				} finally {
					if (_didIteratorError) {
						throw _iteratorError;
					}
				}
			}

			return false;
		},
		_ihash: async(regeneratorRuntime.mark(function callee$1$2(val) {
			var _this = this;

			var _iteratorNormalCompletion, _didIteratorError, _iteratorError, _iterator, _step, image, hash;

			return regeneratorRuntime.wrap(function callee$1$2$(context$2$0) {
				while (1) switch (context$2$0.prev = context$2$0.next) {
					case 0:
						_iteratorNormalCompletion = true;
						_didIteratorError = false;
						_iteratorError = undefined;
						context$2$0.prev = 3;
						_iterator = _this._post.images.data[Symbol.iterator]();

					case 5:
						if (_iteratorNormalCompletion = (_step = _iterator.next()).done) {
							context$2$0.next = 16;
							break;
						}

						image = _step.value;

						if (image instanceof Attachment) {
							context$2$0.next = 9;
							break;
						}

						return context$2$0.abrupt("continue", 13);

					case 9:
						return context$2$0.delegateYield(ImagesHashStorage.getHash(image), "t20", 10);

					case 10:
						hash = context$2$0.t20;

						if (!(hash === val)) {
							context$2$0.next = 13;
							break;
						}

						return context$2$0.abrupt("return", true);

					case 13:
						_iteratorNormalCompletion = true;
						context$2$0.next = 5;
						break;

					case 16:
						context$2$0.next = 22;
						break;

					case 18:
						context$2$0.prev = 18;
						context$2$0.t21 = context$2$0["catch"](3);
						_didIteratorError = true;
						_iteratorError = context$2$0.t21;

					case 22:
						context$2$0.prev = 22;
						context$2$0.prev = 23;

						if (!_iteratorNormalCompletion && _iterator["return"]) {
							_iterator["return"]();
						}

					case 25:
						context$2$0.prev = 25;

						if (!_didIteratorError) {
							context$2$0.next = 28;
							break;
						}

						throw _iteratorError;

					case 28:
						return context$2$0.finish(25);

					case 29:
						return context$2$0.finish(22);

					case 30:
						return context$2$0.abrupt("return", false);

					case 31:
					case "end":
						return context$2$0.stop();
				}
			}, callee$1$2, this, [[3, 18, 22, 30], [23,, 25, 29]]);
		})),
		_subj: function _subj(val) {
			var pSubj = this._post.subj;
			return pSubj ? !val || val.test(pSubj) : false;
		},
		_name: function _name(val) {
			var pName = this._post.posterName;
			return pName ? !val || pName.includes(val) : false;
		},
		_trip: function _trip(val) {
			var pTrip = this._post.posterTrip;
			return pTrip ? !val || pTrip.includes(val) : false;
		},
		_img: function _img(val) {
			var hide;var images = this._post.images;
			var _val = _slicedToArray(val, 3);

			var compareRule = _val[0];
			var weightVals = _val[1];
			var sizeVals = _val[2];

			if (!val) {
				return images.hasAttachments;
			}
			var _iteratorNormalCompletion = true;
			var _didIteratorError = false;
			var _iteratorError = undefined;

			try {
				for (var _iterator = images.data[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {
					var image = _step.value;

					if (!(image instanceof Attachment)) {
						continue;
					}
					if (weightVals) {
						var w = image.weight;
						switch (compareRule) {
							case 0:
								hide = w >= weightVals[0] && w <= weightVals[1];break;
							case 1:
								hide = w < weightVals[0];break;
							case 2:
								hide = w > weightVals[0];break;
						}
						if (!hide) {
							continue;
						} else if (!sizeVals) {
							return true;
						}
					}
					if (sizeVals) {
						var w = image.width,
						    h = image.height;
						switch (compareRule) {
							case 0:
								if (w >= sizeVals[0] && w <= sizeVals[1] && h >= sizeVals[2] && h <= sizeVals[3]) {
									return true;
								}
								break;
							case 1:
								if (w < sizeVals[0] && h < sizeVals[3]) {
									return true;
								}
								break;
							case 2:
								if (w > sizeVals[0] && h > sizeVals[3]) {
									return true;
								}
						}
					}
				}
			} catch (err) {
				_didIteratorError = true;
				_iteratorError = err;
			} finally {
				try {
					if (!_iteratorNormalCompletion && _iterator["return"]) {
						_iterator["return"]();
					}
				} finally {
					if (_didIteratorError) {
						throw _iteratorError;
					}
				}
			}

			return false;
		},
		_sage: function _sage(val) {
			return this._post.sage;
		},
		_op: function _op(val) {
			return this._post.isOp;
		},
		_tlen: function _tlen(val) {
			var text = this._post.text.replace(/^\s+|\s+$|\s+(?=\s)|\n/g, "");
			return !val ? !!text : this._tlenNum_helper(val, text.length);
		},
		_all: function _all(val) {
			return true;
		},
		_video: function _video(val) {
			return this._videoVauthor(val, false);
		},
		_wipe: function _wipe(val) {
			var arr,
			    len,
			    x,
			    txt = this._post.text;
		
			if (val & 1) {
				arr = txt.replace(/>/g, "").split(/\s*\n\s*/);
				if ((len = arr.length) > 5) {
					arr.sort();
					for (var i = 0, n = len / 4; i < len;) {
						x = arr[i];
						var j = 0;
						while (arr[i++] === x) {
							j++;
						}
						if (j > 4 && j > n && x) {
							this._wipeMsg = [1, "\"" + x.substr(0, 20) + "\" x" + (j + 1)];
							return true;
						}
					}
				}
			}
		
			if (val & 2) {
				arr = txt.replace(/[\s\.\?\!,>]+/g, " ").toUpperCase().split(" ");
				if ((len = arr.length) > 3) {
					arr.sort();
					var keys = 0;
					for (var i = 0, n = len / 4, pop = 0; i < len; keys++) {
						x = arr[i];
						var j = 0;
						while (arr[i++] === x) {
							j++;
						}
						if (len > 25) {
							if (j > pop && x.length > 2) {
								pop = j;
							}
							if (pop >= n) {
								this._wipeMsg = [2, "same \"" + x.substr(0, 20) + "\" x" + (pop + 1)];
								return true;
							}
						}
					}
					x = keys / len;
					if (x < 0.25) {
						this._wipeMsg = [2, "uniq " + (x * 100).toFixed(0) + "%"];
						return true;
					}
				}
			}
		
			if (val & 4) {
				arr = txt.replace(/https*:\/\/.*?(\s|$)/g, "").replace(/[\s\.\?!,>:;-]+/g, " ").split(" ");
				if (arr[0].length > 50 || (len = arr.length) > 1 && arr.join("").length / len > 10) {
					this._wipeMsg = [4, null];
					return true;
				}
			}
		
			if (val & 8) {
				var _txt = txt.replace(/\s+/g, "");
				if ((len = _txt.length) > 30 && (x = _txt.replace(/[0-9a-zа-я\.\?!,]/ig, "").length / len) > 0.4) {
					this._wipeMsg = [8, (x * 100).toFixed(0) + "%"];
					return true;
				}
			}
		
			if (val & 16) {
				arr = txt.replace(/[\s\.\?!;,-]+/g, " ").trim().split(" ");
				if ((len = arr.length) > 4) {
					var n = 0,
					    capsw = 0,
					    casew = 0;
					for (var i = 0; i < len; i++) {
						x = arr[i];
						if ((x.match(/[a-zа-я]/ig) || []).length < 5) {
							continue;
						}
						if ((x.match(/[A-ZА-Я]/g) || []).length > 2) {
							casew++;
						}
						if (x === x.toUpperCase()) {
							capsw++;
						}
						n++;
					}
					if (capsw / n >= 0.3 && n > 4) {
						this._wipeMsg = [16, "CAPS " + capsw / arr.length * 100 + "%"];
						return true;
					} else if (casew / n >= 0.3 && n > 8) {
						this._wipeMsg = [16, "cAsE " + casew / arr.length * 100 + "%"];
						return true;
					}
				}
			}
		
			if (val & 32) {
				var _txt = txt.replace(/\s+/g, " ").replace(/>>\d+|https*:\/\/.*?(?: |$)/g, "");
				if ((len = _txt.length) > 30 && (x = (len - _txt.replace(/\d/g, "").length) / len) > 0.4) {
					this._wipeMsg = [32, Math.round(x * 100) + "%"];
					return true;
				}
			}
		
			if (val & 64) {
				if (/(?:\n\s*){10}/i.test(txt)) {
					this._wipeMsg = [64, null];
					return true;
				}
			}
			return false;
		},
		_num: function _num(val) {
			return this._tlenNum_helper(val, this._post.count + 1);
		},
		_tlenNum_helper: function _tlenNum_helper(val, num) {
			var i, arr;
			for (arr = val[0], i = arr.length - 1; i >= 0; --i) {
				if (arr[i] === num) {
					return true;
				}
			}
			for (arr = val[1], i = arr.length - 1; i >= 0; --i) {
				if (num >= arr[i][0] && num <= arr[i][1]) {
					return true;
				}
			}
			return false;
		},
		_vauthor: function _vauthor(val) {
			return this._videoVauthor(val, true);
		},
		_videoVauthor: function _videoVauthor(val, isAuthorSpell) {
			var videos = this._post.videos;
			if (!val) {
				return !!videos.hasLinks;
			}
			if (!videos.hasLinks || !Cfg.YTubeTitles) {
				return false;
			}
			var _iteratorNormalCompletion = true;
			var _didIteratorError = false;
			var _iteratorError = undefined;

			try {
				for (var _iterator = videos.vData[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {
					var siteData = _step.value;
					var _iteratorNormalCompletion2 = true;
					var _didIteratorError2 = false;
					var _iteratorError2 = undefined;

					try {
						for (var _iterator2 = siteData[Symbol.iterator](), _step2; !(_iteratorNormalCompletion2 = (_step2 = _iterator2.next()).done); _iteratorNormalCompletion2 = true) {
							var data = _step2.value;

							if (isAuthorSpell ? val === data[1] : val.test(data[0])) {
								return true;
							}
						}
					} catch (err) {
						_didIteratorError2 = true;
						_iteratorError2 = err;
					} finally {
						try {
							if (!_iteratorNormalCompletion2 && _iterator2["return"]) {
								_iterator2["return"]();
							}
						} finally {
							if (_didIteratorError2) {
								throw _iteratorError2;
							}
						}
					}
				}
			} catch (err) {
				_didIteratorError = true;
				_iteratorError = err;
			} finally {
				try {
					if (!_iteratorNormalCompletion && _iterator["return"]) {
						_iterator["return"]();
					}
				} finally {
					if (_didIteratorError) {
						throw _iteratorError;
					}
				}
			}

			if (videos.linksCount === videos.loadedLinksCount) {
				return false;
			}
			return new Promise(function (resolve, reject) {
				videos.titleLoadFn = function (data) {
					if (isAuthorSpell ? val === data[1] : val.test(data[0])) {
						resolve(true);
					} else if (videos.linksCount === videos.loadedLinksCount) {
						resolve(false);
					} else {
						return;
					}
					videos.titleLoadFn = null;
				};
			});
		}
	};

	function disableSpells() {
		if (spells.enable) {
			sVis = aib.t ? "1".repeat(dForm.firstThr.pcount).split("") : [];
			for (var post = dForm.firstThr.op; post; post = post.next) {
				if (post.spellHidden && !post.userToggled) {
					post.spellUnhide();
				}
			}
		}
		closeAlert($id("de-alert-help-err-spell"));
	}

	function toggleSpells() {
		var temp,
		    fld = $id("de-spell-edit"),
		    val = fld.value;
		if (val && (temp = spells.parseText(val))) {
			disableSpells();
			spells.setSpells(temp, true);
			fld.value = spells.list;
		} else {
			if (val) {
				locStorage["__de-spells"] = "{\"hide\": false, \"data\": null}";
			} else {
				disableSpells();
				spells.disable();
				saveCfg("spells", "");
				locStorage["__de-spells"] = "{\"hide\": false, \"data\": \"\"}";
			}
			locStorage.removeItem("__de-spells");
			$q("input[info=\"hideBySpell\"]", doc).checked = spells.enable = false;
		}
	}

	function addSpell(type, arg, isNeg) {
		var temp,
		    fld = $id("de-spell-edit"),
		    val = fld && fld.value,
		    chk = $q("input[info=\"hideBySpell\"]", doc);
		if (!val || (temp = spells.parseText(val))) {
			disableSpells();
			spells.addSpell(type, arg, aib.t ? [aib.b, aib.t] : null, isNeg, temp);
			val = spells.list;
			saveCfg("hideBySpell", !!val);
			if (val) {
				var sRunner = new SpellsRunner();
				for (var post = dForm.firstThr.op; post; post = post.next) {
					sRunner.run(post);
				}
				sRunner.end();
			} else {
				saveCfg("spells", "");
				spells.enable = false;
			}
			if (fld) {
				chk.checked = !!(fld.value = val);
			}
			return;
		}
		spells.enable = false;
		if (chk) {
			chk.checked = false;
		}
	}




	function PostForm(form, ignoreForm, dc) {
		var _this = this;

		this.oeForm = $q("form[name=\"oeform\"], form[action*=\"paint\"]", dc);
		if (!ignoreForm && !form) {
			if (this.oeForm) {
				ajaxLoad(aib.getThrdUrl(aib.b, dForm.firstThr.num), false).then(function (loadedDoc) {
					pr = new PostForm($q(aib.qPostForm, loadedDoc), true, loadedDoc);
				}, function () {
					pr = new PostForm(null, true, dc);
				});
			} else {
				this.form = null;
			}
			return;
		}
		function $x(path, root) {
			return dc.evaluate(path, root, null, 8, null).singleNodeValue;
		}
		var p = ".//tr[not(contains(@style,\"none\"))]//input[not(@type=\"hidden\") and ";
		this.tNum = aib.t;
		this.form = form;
		this.cap = $q("input[type=\"text\"][name*=\"aptcha\"], div[id*=\"captcha\"]", form);
		this.txta = $q("tr:not([style*=\"none\"]) textarea:not([style*=\"display:none\"])", form);
		this.subm = $q("tr input[type=\"submit\"]", form);
		this.file = $q("tr input[type=\"file\"]", form);
		if (this.file) {
			this.fileTd = $parent(this.file, "TD");
			this.spoil = $q("input[type=\"checkbox\"][name=\"spoiler\"]", this.fileTd);
		}
		this.passw = $q("tr input[type=\"password\"]", form);
		this.dpass = $q("input[type=\"password\"], input[name=\"password\"]", dForm.el);
		this.name = $x(p + "(@name=\"field1\" or @name=\"name\" or @name=\"internal_n\" or @name=\"nya1\" or @name=\"akane\")]", form);
		this.mail = $x(p + (aib._410 ? "@name=\"sage\"]" : "(@name=\"field2\" or @name=\"em\" or @name=\"sage\" or @name=\"email\" or @name=\"nya2\" or @name=\"nabiki\" or @name=\"dont_bump\")]"), form);
		this.subj = $x(p + "(@name=\"field3\" or @name=\"sub\" or @name=\"subject\" or @name=\"internal_s\" or @name=\"nya3\" or @name=\"kasumi\")]", form);
		this.video = $q("tr input[name=\"video\"], tr input[name=\"embed\"]", form);
		this.gothr = aib.qPostRedir && (p = $q(aib.qPostRedir, form)) && $parent(p, "TR");
		this.pForm = $New("div", { id: "de-pform" }, [this.form, this.oeForm]);
		dForm.el.insertAdjacentHTML("beforebegin", "<div class=\"de-parea\"><div>[<a href=\"#\"></a>]</div><hr></div>");
		this.pArea[0] = dForm.el.previousSibling;
		this._pBtn[0] = this.pArea[0].firstChild;
		this._pBtn[0].firstElementChild.onclick = this.showMainReply.bind(this, false);
		var el = aib.fch ? $c("board", dForm.el) : dForm.el;
		el.insertAdjacentHTML("afterend", "<div class=\"de-parea\"><div>[<a href=\"#\"></a>]</div><hr></div>");
		this.pArea[1] = el.nextSibling;
		this._pBtn[1] = this.pArea[1].firstChild;
		this._pBtn[1].firstElementChild.onclick = this.showMainReply.bind(this, true);
		this.qArea = $add("<div style=\"display: none; " + Cfg.qReplyX + "; " + Cfg.qReplyY + ";\" id=\"de-qarea\" class=\"" + aib.cReply + (Cfg.hangQReply ? " de-qarea-hanging" : " de-qarea-inline") + "\"></div>");
		this.isBottom = Cfg.addPostForm === 1;
		this.setReply(false, !aib.t || Cfg.addPostForm > 1);
		el = this.qArea;
		el.insertAdjacentHTML("beforeend", "<div id=\"de-resizer-top\"></div>" + "<div" + (Cfg.hangQReply ? " class=\"de-cfg-head\"" : "") + ">" + "<span id=\"de-qarea-target\"></span>" + "<span id=\"de-qarea-utils\">" + "<span id=\"de-qarea-toggle\" title=\"" + Lng.toggleQReply[lang] + "\">❐</span>" + "<span id=\"de-qarea-close\" title=\"" + Lng.closeQReply[lang] + "\">✖</span>" + "</span></div>" + "<div id=\"de-resizer-left\"></div>" + "<div id=\"de-resizer-right\"></div>" + "<div id=\"de-resizer-bottom\"></div>");
		el = el.firstChild.nextSibling;
		el.lang = getThemeLang();
		el.addEventListener("mousedown", {
			_el: this.qArea,
			_elStyle: this.qArea.style,
			_oldX: 0,
			_oldY: 0,
			_X: 0,
			_Y: 0,
			handleEvent: function handleEvent(e) {
				if (!Cfg.hangQReply) {
					return;
				}
				var curX = e.clientX,
				    curY = e.clientY;
				switch (e.type) {
					case "mousedown":
						this._oldX = curX;
						this._oldY = curY;
						this._X = Cfg.qReplyX;
						this._Y = Cfg.qReplyY;
						doc.body.addEventListener("mousemove", this, false);
						doc.body.addEventListener("mouseup", this, false);
						$pd(e);
						return;
					case "mousemove":
						var maxX = Post.sizing.wWidth - this._el.offsetWidth,
						    maxY = Post.sizing.wHeight - this._el.offsetHeight - 25,
						    cr = this._el.getBoundingClientRect(),
						    x = cr.left + curX - this._oldX,
						    y = cr.top + curY - this._oldY;
						this._X = x >= maxX || curX > this._oldX && x > maxX - 20 ? "right: 0" : x < 0 || curX < this._oldX && x < 20 ? "left: 0" : "left: " + x + "px";
						this._Y = y >= maxY || curY > this._oldY && y > maxY - 20 ? "bottom: 25px" : y < 0 || curY < this._oldY && y < 20 ? "top: 0" : "top: " + y + "px";
						this._elStyle.cssText = this._X + "; " + this._Y;
						this._oldX = curX;
						this._oldY = curY;
						return;
					default:
					
						doc.body.removeEventListener("mousemove", this, false);
						doc.body.removeEventListener("mouseup", this, false);
						saveCfg("qReplyX", this._X);
						saveCfg("qReplyY", this._Y);
				}
			}
		}, false);
		el = el.lastChild;
		el.firstChild.onclick = function () {
			var node = _this.qArea;
			toggleCfg("hangQReply");
			if (Cfg.hangQReply) {
				node.className = aib.cReply + " de-qarea-hanging";
				node = node.firstChild.nextSibling;
				node.className = "de-cfg-head";
			} else {
				node.className = aib.cReply + " de-qarea-inline";
				node = node.firstChild.nextSibling;
				node.removeAttribute("class");
				_this.txta.focus();
			}
		};
		el.lastChild.onclick = this.closeQReply.bind(this);
		if (aib.tire) {
			$each($Q("input[type=\"hidden\"]", dForm.el), $del);
			dForm.el.appendChild($c("userdelete", doc.body));
			this.dpass = $q("input[type=\"password\"]", dForm.el);
		}
		if (!this.form || !this.txta) {
			return;
		}
		new FormResizer("top", el = this.qArea.firstChild, this);
		new FormResizer("left", el = el.nextSibling.nextSibling, this);
		new FormResizer("right", el = el.nextSibling, this);
		new FormResizer("bottom", el = el.nextSibling, this);
		if (!aib.kus && (aib.multiFile || !Cfg.fileThumb)) {
			this.setPlaceholders();
		}
		aib.disableRedirection(this.form);
		this.form.style.display = "inline-block";
		this.form.style.textAlign = "left";
		if (nav.Firefox) {
			this.txta.addEventListener("mouseup", function () {
				saveCfg("textaWidth", parseInt(this.style.width, 10));
				saveCfg("textaHeight", parseInt(this.style.height, 10));
			}, false);
		} else {
			this.txta.insertAdjacentHTML("afterend", "<div id=\"de-txta-resizer\"></div>");
			this.txta.nextSibling.addEventListener("mousedown", {
				_el: this.txta,
				_elStyle: this.txta.style,
				handleEvent: function handleEvent(e) {
					switch (e.type) {
						case "mousedown":
							doc.body.addEventListener("mousemove", this, false);
							doc.body.addEventListener("mouseup", this, false);
							$pd(e);
							return;
						case "mousemove":
							var cr = this._el.getBoundingClientRect();
							this._elStyle.width = e.clientX - cr.left + "px";
							this._elStyle.height = e.clientY - cr.top + "px";
							return;
						default:
						
							doc.body.removeEventListener("mousemove", this, false);
							doc.body.removeEventListener("mouseup", this, false);
							saveCfg("textaWidth", parseInt(this._elStyle.width, 10));
							saveCfg("textaHeight", parseInt(this._elStyle.height, 10));
					}
				}
			}, false);
		}
		if (aib.kus) {
			while (this.subm.nextSibling) {
				$del(this.subm.nextSibling);
			}
		}
		if (!aib.iich && Cfg.addSageBtn && this.mail) {
			el = $parent(this.mail, "LABEL") || this.mail;
			if (el.nextElementSibling || el.previousElementSibling) {
				el.style.display = "none";
			} else {
				$parent(this.mail, "TR").style.display = "none";
			}
			el = $new("span", { id: "de-sagebtn", "class": "de-btn-sage" }, { click: function (e) {
					e.stopPropagation();
					$pd(e);
					toggleCfg("sageReply");
					_this._setSage();
				} });
			$after(this.subm, el);
			setTimeout(this._setSage.bind(this), 0);
			if (aib._2chru) {
				while (el.nextSibling) {
					$del(el.nextSibling);
				}
			}
		}
		this.addTextPanel();
		this.txta.classList.add("de-textarea");
		this.txta.style.cssText = "width: " + Cfg.textaWidth + "px; height: " + Cfg.textaHeight + "px;";
		this.txta.addEventListener("keypress", function (e) {
			var code = e.charCode || e.keyCode;
			if ((code === 33 || code === 34) && e.which === 0) {
				e.target.blur();
				window.focus();
			}
		}, false);
		if (!aib.tiny) {
			this.subm.value = Lng.reply[lang];
		}
		this.subm.addEventListener("click", function (e) {
			if (aib._2chru && !aib.reqCaptcha) {
				$ajax("/" + aib.b + "/api/requires-captcha").then(function (xhr) {
					aib.reqCaptcha = true;
					if (JSON.parse(xhr.responseText)["requires-captcha"] !== "1") {
						_this.subm.click();
						return;
					}
					$id("captcha_tr").style.display = "table-row";
					$id("captchaimage").src = "/" + aib.b + "/captcha?" + Math.random();
					$after(_this.cap, $new("span", {
						"class": "shortened",
						style: "margin: 0px 0.5em;",
						text: "проверить капчу" }, {
						click: function () {
							var _this2 = this;

							$ajax("/" + aib.b + "/api/validate-captcha", { method: "POST" }).then(function (xhr) {
								if (JSON.parse(xhr.responseText).status === "ok") {
									_this2.innerHTML = "можно постить";
								} else {
									_this2.innerHTML = "неверная капча";
									setTimeout(function (el) {
										return _this2.innerHTML = "проверить капчу";
									}, 1000);
								}
							}, emptyFn);
						}
					}));
				}, emptyFn);
				$pd(e);
				return;
			}
			if (Cfg.warnSubjTrip && _this.subj && /#.|##./.test(_this.subj.value)) {
				$pd(e);
				$alert(Lng.subjHasTrip[lang], "upload", false);
				return;
			}
			var val = _this.txta.value;
			if (spells.haveOutreps) {
				val = spells.outReplace(val);
			}
			if (_this.tNum && pByNum[_this.tNum].subj === "Dollchan Extension Tools") {
				var temp = "\n\n" + _this._wrapText(aib.markupBB, aib.markupTags[5], "-".repeat(50) + "\n" + nav.ua + "\nv" + version + " [" + nav.scriptInstall + "]")[1];
				if (!val.includes(temp)) {
					val += temp;
				}
			}
			_this.txta.value = val;
			if (Cfg.ajaxReply) {
				$alert(Lng.checking[lang], "upload", true);
			}
			if (_this.video && (val = _this.video.value) && (val = val.match(Videos.ytReg))) {
				_this.video.value = "http://www.youtube.com/watch?v=" + val[1];
			}
			if (_this.isQuick) {
				_this.pForm.style.display = "none";
				_this.qArea.style.display = "none";
				$after(_this._pBtn[+_this.isBottom], _this.pForm);
			}
			updater.pause();
		}, false);
		if (Cfg.noGoto && this.gothr) {
			this.gothr.style.display = "none";
		}
		if (Cfg.noPassword && this.passw) {
			$parent(this.passw, "TR").style.display = "none";
		}
		if (Cfg.noName && this.name) {
			$parent(this.name, "TR").style.display = "none";
		}
		window.addEventListener("load", function () {
			if (Cfg.userName && _this.name) {
				setTimeout(PostForm.setUserName, 1000);
			}
			if (_this.passw) {
				setTimeout(PostForm.setUserPassw, 1000);
			}
		}, false);
		if (this.cap) {
			this.capTr = $parent(this.cap, "TR");
			this.txta.addEventListener("focus", this._captchaInit.bind(this, this.capTr.innerHTML), false);
			this.form.addEventListener("click", this._captchaInit.bind(this, this.capTr.innerHTML), false);
			if (!aib.krau) {
				this.capTr.style.display = "none";
			}
			if (!aib.mak && !aib.fch && !$id("recaptcha_widget_div")) {
				this.capTr.innerHTML = "";
			}
			this.cap = null;
		}
		if (Cfg.ajaxReply === 2) {
			if (aib.krau) {
				this.form.removeAttribute("onsubmit");
			}
			setTimeout(function () {
				_this.form.onsubmit = function (e) {
					$pd(e);
					if (aib.krau) {
						aib.addProgressTrack.click();
					}
					if (aib._2chru) {
						doc.body.insertAdjacentHTML("beforeend", "<iframe class=\"ninja\" id=\"csstest\" src=\"/" + aib.b + "/csstest.foo\"></iframe>");
						doc.body.lastChild.onload = function (e) {
							$del(e.target);
							spawn(html5Submit, _this.form, true).then(doUploading);
						};
						return;
					}
					spawn(html5Submit, _this.form, true).then(doUploading);
				};
			}, 0);
		} else if (Cfg.ajaxReply === 1) {
			this.form.target = "de-iframe-pform";
			this.form.onsubmit = null;
		}
		el = this.file;
		if (el) {
			aib.fixFileInputs(el);
			this.eventFiles(true);
		}
	}
	PostForm.setUserName = function () {
		var el = $q("input[info=\"nameValue\"]", doc);
		if (el) {
			saveCfg("nameValue", el.value);
		}
		pr.name.value = Cfg.userName ? Cfg.nameValue : "";
	};
	PostForm.setUserPassw = function () {
		var el = $q("input[info=\"passwValue\"]", doc);
		if (el) {
			saveCfg("passwValue", el.value);
		}
		(pr.dpass || {}).value = pr.passw.value = Cfg.passwValue;
	};
	PostForm.prototype = Object.defineProperties({
		fileObj: null,
		filesCount: 0,
		isHidden: false,
		isQuick: false,
		isBottom: false,
		lastQuickPNum: -1,
		pForm: null,
		pArea: [],
		qArea: null,
		addTextPanel: function addTextPanel() {
			var id,
			    val,
			    btns,
			    html = "",
			    tPanel = $id("de-txt-panel");
			if (!Cfg.addTextBtns) {
				$del(tPanel);
				return;
			}
			if (!tPanel) {
				tPanel = $new("span", { id: "de-txt-panel" }, { click: this, mouseover: this });
			}
			tPanel.style.cssFloat = Cfg.txtBtnsLoc ? "none" : "right";
			$after(Cfg.txtBtnsLoc ? $id("de-txta-resizer") || this.txta : aib._420 ? $c("popup", this.form) : this.subm, tPanel);
			id = ["bold", "italic", "under", "strike", "spoil", "code", "sup", "sub", "quote"], val = ["B", "i", "U", "S", "%", "C", "v", "^", "&gt;"];
			btns = aib.markupTags;
			for (var i = 0, len = btns.length; i < len; ++i) {
				if (btns[i] === "") {
					continue;
				}
				html += "<span id=\"de-btn-" + id[i] + "\" de-title=\"" + Lng.txtBtn[i][lang] + "\" de-tag=\"" + btns[i] + "\">" + (Cfg.addTextBtns === 2 ? (html === "" ? "[ " : "") + "<a class=\"de-abtn\" href=\"#\">" + val[i] + "</a>" + (i === len - 1 ? " ]" : " / ") : Cfg.addTextBtns === 3 ? "<button type=\"button\" style=\"font-weight: bold;\">" + val[i] + "</button>" : "") + "</span>";
			}
			tPanel.innerHTML = html;
		},
		delFilesUtils: function delFilesUtils() {
			for (var inp = this.fileObj; inp; inp = inp.next) {
				inp.delUtils();
			}
		},
		eventFiles: function eventFiles(clear) {
			var last = null,
			    els = $Q("input[type=\"file\"]", this.fileTd);
			for (var i = 0, len = els.length; i < len; ++i) {
				var el = els[i],
				    inp = el.obj;
				if (inp) {
					inp.prev = last;
					if (last) {
						last.next = inp;
					}
					last = inp;
				} else {
					el.obj = last = new FileInput(this, el, last);
					last.init(false);
					if (clear && el.files && el.files.length) {
						last.clear();
					}
				}
			}
			this.fileObj = els[0].obj;
		},
		handleEvent: function handleEvent(e) {
			var id,
			    el = e.target;
			if (el.tagName !== "SPAN") {
				el = el.parentNode;
			}
			id = el.id;
			if (id.startsWith("de-btn")) {
				var x;
				if (e.type === "mouseover") {
					if (id === "de-btn-quote") {
						quotetxt = $txtSelect();
					}
					x = -1;
					if (hKeys) {
						switch (id.substr(7)) {
							case "bold":
								x = 12;break;
							case "italic":
								x = 13;break;
							case "strike":
								x = 14;break;
							case "spoil":
								x = 15;break;
							case "code":
								x = 16;
						}
					}
					KeyEditListener.setTitle(el, x);
					return;
				}
				x = pr.txta;
				var start = x.selectionStart,
				    end = x.selectionEnd,
				    q = Cfg.spacedQuote ? "> " : ">";
				if (id === "de-btn-quote") {
					$txtInsert(x, q + (start === end ? quotetxt : x.value.substring(start, end)).replace(/\n/gm, "\n" + q));
					quotetxt = "";
				} else {
					var scrtop = x.scrollTop,
					    val = this._wrapText(aib.markupBB, el.getAttribute("de-tag"), x.value.substring(start, end)),
					    len = start + val[0];
					x.value = x.value.substr(0, start) + val[1] + x.value.substr(end);
					x.setSelectionRange(len, len);
					x.focus();
					x.scrollTop = scrtop;
				}
				$pd(e);
				e.stopPropagation();
			}
		},
		showQuickReply: function showQuickReply(post, pNum, closeReply, isNumClick) {
			var temp,
			    isThr = aib.t,
			    qNum = post.tNum;
			if (!this.isQuick) {
				this.isQuick = true;
				this.setReply(true, false);
				$t("a", this._pBtn[+this.isBottom]).className = "de-abtn de-parea-btn-" + (isThr ? "reply" : "thrd");
				if (!isThr && !aib.kus && !aib.dobr && !aib.mak) {
					if (this.oeForm) {
						$del($q("input[name=\"oek_parent\"]", this.oeForm));
						this.oeForm.insertAdjacentHTML("afterbegin", "<input type=\"hidden\" value=\"" + qNum + "\" name=\"oek_parent\">");
					}
					if (this.form) {
						$del($q("input[name=\"" + aib.thrid + "\"]", this.form));
						this.form.insertAdjacentHTML("afterbegin", "<input type=\"hidden\" id=\"de_thrid\" value=\"" + qNum + "\" name=\"" + aib.thrid + "\">");
					}
				}
			} else if (closeReply && !quotetxt && post.wrap.nextElementSibling === this.qArea) {
				this.closeQReply();
				return;
			}
			$after(post.wrap, this.qArea);
			if (!isThr) {
				this._toggleQuickReply(qNum);
			}
			if (!this.form) {
				return;
			}
			if (this._lastCapUpdate && (!isThr && this.tNum !== qNum || Date.now() - this._lastCapUpdate > 300000)) {
				this.tNum = qNum;
				this.refreshCapImg(false);
			}
			this.tNum = qNum;
			if (aib._420 && this.txta.value === "Comment") {
				this.txta.value = "";
			}
			temp = this.txta.value;
			if (!Cfg.addOPLink && !isThr && post.isOp && !isNumClick) {
				this.txta.focus();
			} else {
				$txtInsert(this.txta, (isNumClick ? ">>" + pNum : (temp !== "" && temp.slice(-1) !== "\n" ? "\n" : "") + (this.lastQuickPNum === pNum && temp.includes(">>" + pNum) ? "" : ">>" + pNum + "\n")) + (quotetxt ? quotetxt.replace(/^\n|\n$/g, "").replace(/(^|\n)(.)/gm, "$1>" + (Cfg.spacedQuote ? " " : "") + "$2") + "\n" : ""));
			}
			temp = pByNum[pNum].thr.op.title;
			if (temp.length > 27) {
				temp = temp.substr(0, 30) + "…";
			}
			$id("de-qarea-target").textContent = temp || "#" + pNum;
			this.lastQuickPNum = pNum;
		},
		showMainReply: function showMainReply(isBottom, evt) {
			this.closeQReply();
			if (!aib.t) {
				this.tNum = false;
				this.refreshCapImg(false);
			}
			if (this.isBottom === isBottom) {
				this.pForm.style.display = this.isHidden ? "" : "none";
				this.isHidden = !this.isHidden;
				this.updatePAreaBtns();
			} else {
				this.isBottom = isBottom;
				this.setReply(false, false);
			}
			if (evt) {
				$pd(evt);
			}
		},
		closeQReply: function closeQReply() {
			if (this.isQuick) {
				this.isQuick = false;
				this.lastQuickPNum = -1;
				if (!aib.t) {
					this._toggleQuickReply(0);
					$del($id("de_thrid"));
				}
				this.setReply(false, !aib.t || Cfg.addPostForm > 1);
			}
		},
		refreshCapImg: function refreshCapImg(focus) {
			if (this._lastCapUpdate) {
				this._lastCapUpdate = Date.now();
			}
			if (aib.mak || aib.fch) {
				aib.updateCaptcha(focus);
			} else {
				if (!this.cap || aib.krau && !$q("input[name=\"captcha_name\"]", this.form).hasAttribute("value")) {
					return;
				}
				var img = this.recap ? $id("recaptcha_image") : $t("img", this.capTr);
				if (aib.dobr || aib.krau || aib.dvachnet || this.recap) {
					img.click();
				} else if (img) {
					var src = img.getAttribute("src");
					if (aib.tire) {
						src = "/" + aib.b + "/captcha.fpl?" + Math.random();
					} else if (aib.kus || aib.tinyIb) {
						src = src.replace(/\?[^?]+$|$/, (aib._410 ? "?board=" + aib.b + "&" : "?") + Math.random());
					} else {
						src = src.replace(/pl$/, "pl?key=mainpage&amp;dummy=").replace(/dummy=[\d\.]*/, "dummy=" + Math.random());
						src = this.tNum ? src.replace(/mainpage|res\d+/, "res" + this.tNum) : src.replace(/res\d+/, "mainpage");
					}
					img.src = "";
					img.src = src;
				}
			}
			if (this.cap) {
				this.cap.value = "";
				if (focus) {
					this.cap.focus();
				}
			}
		},
		setReply: function setReply(quick, hide) {
			if (quick) {
				$before($id("de-resizer-right"), this.pForm);
			} else {
				$after(this.pArea[+this.isBottom], this.qArea);
				$after(this._pBtn[+this.isBottom], this.pForm);
			}
			this.isHidden = hide;
			this.qArea.style.display = quick ? "" : "none";
			this.pForm.style.display = hide ? "none" : "";
			this.updatePAreaBtns();
		},
		setPlaceholders: function setPlaceholders() {
			this._setPlaceholder("name");
			this._setPlaceholder("subj");
			this._setPlaceholder("mail");
			this._setPlaceholder("cap");
			this._setPlaceholder("video");
		},
		updatePAreaBtns: function updatePAreaBtns() {
			var txt = "de-abtn de-parea-btn-",
			    rep = aib.t ? "reply" : "thrd";
			$t("a", this._pBtn[+this.isBottom]).className = txt + (this.pForm.style.display === "" ? "close" : rep);
			$t("a", this._pBtn[+!this.isBottom]).className = txt + rep;
		},

		_lastCapUpdate: 0,
		_pBtn: [],
		_setSage: function _setSage() {
			var el = $id("de-sagebtn"),
			    c = Cfg.sageReply;
			el.style.opacity = c ? "1" : ".3";
			el.title = c ? "SAGE!" : Lng.noSage[lang];
			if (this.mail.type === "text") {
				this.mail.value = c ? "sage" : aib.fch ? "noko" : "";
			} else {
				this.mail.checked = c;
			}
		},
		_toggleQuickReply: function _toggleQuickReply(tNum) {
			if (this.oeForm) {
				$q("input[name=\"oek_parent\"], input[name=\"replyto\"]", this.oeForm).value = tNum;
			}
			if (this.form) {
				$q("#de_thrid, input[name*=\"thread\"]", this.form).value = tNum;
			}
		},
		_captchaInit: function _captchaInit(html) {
			if (this.capInited) {
				return;
			}
			if (aib.mak || aib.fch) {
				aib.updateCaptcha(false);
				pr.txta.tabIndex = 999;
				this.capInited = true;
				return;
			}
			if (!$id("recaptcha_widget_div")) {
				this.capTr.innerHTML = html;
			}
			this.cap = $q("input[type=\"text\"][name*=\"aptcha\"]:not([name=\"recaptcha_challenge_field\"])", this.capTr);
			if (aib.tire) {
				$script("show_captcha()");
			}
			if (aib.krau) {
				aib.initCaptcha.click();
				$id("captcha_image").setAttribute("onclick", "requestCaptcha(true);");
			}
			if (aib.dvachnet) {
				$script("get_captcha()");
			}
			setTimeout(this._captchaUpd.bind(this), 100);
		},
		_captchaUpd: function _captchaUpd() {
			var _this = this;

			var img, a;
			if ((this.recap = $id("recaptcha_response_field")) && (img = $id("recaptcha_image"))) {
				this.cap = this.recap;
				img.setAttribute("onclick", "Recaptcha.reload()");
				img.style.cssText = "width: 300px; cursor: pointer;";
			}
			this.capInited = true;
			this.cap.autocomplete = "off";
			if (!aib.kus && (aib.multiFile || !Cfg.fileThumb)) {
				this.cap.placeholder = Lng.cap[lang];
			}
			this.cap.onkeypress = (function () {
				var ru = "йцукенгшщзхъфывапролджэячсмитьбюё",
				    en = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`";
				return function (e) {
					if (!Cfg.captchaLang || e.which === 0) {
						return;
					}
					var i,
					    code = e.charCode || e.keyCode,
					    chr = String.fromCharCode(code).toLowerCase();
					if (Cfg.captchaLang === 1) {
						if (code < 1040 || code > 1279 || (i = ru.indexOf(chr)) === -1) {
							return;
						}
						chr = en[i];
					} else {
						if (code < 33 || code > 122 || (i = en.indexOf(chr)) === -1) {
							return;
						}
						chr = ru[i];
					}
					$pd(e);
					$txtInsert(e.target, chr);
				};
			})();
			if (aib.krau) {
				return;
			}
			if (aib.dobr || aib.dvachnet || this.recap || !(img = $q("img", this.capTr))) {
				this.capTr.style.display = "";
				return;
			}
			if (!aib.kus && !aib.tinyIb) {
				this._lastCapUpdate = Date.now();
				this.cap.onfocus = function () {
					if (_this._lastCapUpdate && Date.now() - _this._lastCapUpdate > 300000) {
						_this.refreshCapImg(false);
					}
				};
				if (!aib.t && this.isQuick) {
					this.refreshCapImg(false);
				}
			}
			img.title = Lng.refresh[lang];
			img.alt = Lng.loading[lang];
			img.style.cssText = "vertical-align: text-bottom; border: none; cursor: pointer;";
			img.onclick = this.refreshCapImg.bind(this, true);
			if ((a = img.parentNode).tagName === "A") {
				$after(a, img);
				$del(a);
			}
			this.capTr.style.display = "";
		},
		_setPlaceholder: function _setPlaceholder(val) {
			var el = this[val];
			if (el) {
				if (aib.multiFile || !Cfg.fileThumb) {
					el.placeholder = Lng[val][lang];
				} else {
					el.removeAttribute("placeholder");
				}
			}
		},
		_wrapText: function _wrapText(markupBB, tag, text) {
			var m;
			if (markupBB) {
				var str;
				if (text.includes("\n")) {
					str = "[" + tag + "]" + text + "[/" + tag + "]";
					return [str.length, str];
				}
				m = text.match(/^(\s*)(.*?)(\s*)$/);
				str = m[1] + "[" + tag + "]" + m[2] + "[/" + tag + "]" + m[3];
				return [!m[2].length ? m[1].length + tag.length + 2 : str.length, str];
			}
			var rv = "",
			    i = 0,
			    arr = text.split("\n");
			for (var len = arr.length; i < len; ++i) {
				m = arr[i].match(/^(\s*)(.*?)(\s*)$/);
				rv += "\n" + m[1] + (tag === "^H" ? m[2] + "^H".repeat(m[2].length) : tag + m[2] + tag) + m[3];
			}
			return [i === 1 && !m[2].length && tag !== "^H" ? m[1].length + tag.length : rv.length - 1, rv.slice(1)];
		}
	}, {
		fileArea: {
			get: function () {
				var val;
				if (aib.multiFile) {
					val = $add("<tr><td></td><td><div id=\"de-file-area\"></div></td></tr>");
					$after(this.fileTd.parentNode, val);
				} else {
					val = $t(aib.tiny ? "th" : "td", $parent(this.txta, "TR"));
					val.innerHTML = "";
				}
				Object.defineProperty(this, "fileArea", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		rarInput: {
			get: function () {
				var val = doc.body.appendChild($new("input", { type: "file", style: "display: none;" }, null));
				Object.defineProperty(this, "rarInput", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		isVisible: {
			get: function () {
				if (!this.isHidden && this.isBottom && $q(":focus", this.pForm)) {
					var cr = this.pForm.getBoundingClientRect();
					return cr.bottom > 0 && cr.top < doc.documentElement.clientHeight;
				}
				return false;
			},
			configurable: true,
			enumerable: true
		},
		topCoord: {
			get: function () {
				return this.pForm.getBoundingClientRect().top;
			},
			configurable: true,
			enumerable: true
		}
	});

	function FileInput(form, el, prev) {
		this.el = el;
		this.place = el.parentNode;
		this.form = form;
		this.prev = prev;
		if (prev) {
			prev.next = this;
		}
	}
	FileInput.prototype = Object.defineProperties({
		empty: true,
		next: null,
		imgFile: null,
		thumb: null,
		clear: function clear() {
			var newEl,
			    form = this.form,
			    oldEl = this.el;
			oldEl.insertAdjacentHTML("afterend", oldEl.outerHTML);
			newEl = this.el.nextSibling;
			newEl.obj = this;
			newEl.addEventListener("change", this, false);
			newEl.addEventListener("dragleave", this, false);
			newEl.addEventListener("drop", this, false);
			if (form.file === oldEl) {
				form.file = newEl;
			}
			this.el = newEl;
			$del(oldEl);
			this.empty = true;
			this.hideInputs();
		},
		delUtils: function delUtils() {
			if (Cfg.fileThumb) {
				this.thumb.classList.add("de-file-off");
				if (this._mediaEl) {
					window.URL.revokeObjectURL(this._mediaEl.src);
					this._mediaEl.parentNode.title = Lng.clickToAdd[lang];
					$del(this._mediaEl);
					this._mediaEl = null;
				}
			}
			$del(this._delUtil);
			$del(this._spUtil);
			$del(this._rjUtil);
			this.imgFile = this._delUtil = this._spUtil = this._rjUtil = null;
			this._changeFilesCount(-1);
			this.clear();
		},
		updateUtils: function updateUtils() {
			this.init(true);
			if (this._delUtil) {
				$after(this._buttonsPlace, this._delUtil);
			}
			if (this._spUtil) {
				$after(this._buttonsPlace, this._spUtil);
			}
			if (this._rjUtil) {
				$after(this._buttonsPlace, this._rjUtil);
			}
		},
		handleEvent: function handleEvent(e) {
			var _this = this;

			switch (e.type) {
				case "change":
					setTimeout(this._onFileChange.bind(this), 20);return;
				case "click":
					if (e.target === this._delUtil) {
						this.delUtils();
					} else if (e.target === this._spUtil) {
						this.form.spoil.checked = this._spUtil.checked;
						return;
					} else if (e.target === this._rjUtil) {
						this._addRarJpeg();
					} else if (e.target.className === "de-file-img") {
						this.el.click();
					}
					e.stopPropagation();
					$pd(e);
					return;
				case "dragover":
					this.thumb.classList.add("de-file-drag");
					$after(this.thumb, this.el);
					return;
				case "dragleave":
				case "drop":
					setTimeout(function () {
						_this.thumb.classList.remove("de-file-drag");
						var el = _this.place.firstChild;
						if (el) {
							$before(el, _this.el);
						} else {
							_this.place.appendChild(_this.el);
						}
					}, 10);
					return;
				case "mouseover":
					this.thumb.classList.add("de-file-hover");return;
				case "mouseout":
					this.thumb.classList.remove("de-file-hover");
			}
		},
		hideInputs: function hideInputs() {
			var inp = this.next;
			while (inp && inp.empty) {
				inp = inp.next;
			}
			if (!inp) {
				inp = this;
				while (inp.prev && inp.prev.empty) {
					inp = inp.prev;
				}
				var hideThumbs = Cfg.fileThumb;
				while (inp = inp.next) {
					if (hideThumbs) {
						inp.thumb.style.display = "none";
					} else {
						inp._wrap.style.display = "none";
					}
				}
			}
		},
		init: function init(update) {
			if (Cfg.fileThumb) {
				this.form.fileTd.parentNode.style.display = "none";
				this.form.fileArea.insertAdjacentHTML("beforeend", "<div class=\"de-file de-file-off\"><div class=\"de-file-img\">" + "<div class=\"de-file-img\" title=\"" + Lng.clickToAdd[lang] + "\"></div></div></div>");
				this.thumb = this.form.fileArea.lastChild;
				this.thumb.addEventListener("mouseover", this, false);
				this.thumb.addEventListener("mouseout", this, false);
				this.thumb.addEventListener("click", this, false);
				this.thumb.addEventListener("dragover", this, false);
				this.el.addEventListener("dragleave", this, false);
				this.el.addEventListener("drop", this, false);
				if (update) {
					this._showPviewImage();
				} else if (this.prev) {
					this.thumb.style.display = "none";
				}
			} else if (update) {
				this._wrap.style.display = "";
				this.form.fileTd.parentNode.style.display = "";
				if (this._mediaE) {
					window.URL.revokeObjectURL(this._mediaE.src);
				}
				$del(this.thumb);
				this.thumb = this._mediaEl = null;
			}
			if (!update) {
				this.el.classList.add("de-file-input");
				this.el.addEventListener("change", this, false);
			}
		},

		_mediaEl: null,
		_delUtil: null,
		_spUtil: null,
		_rjUtil: null,
		_addRarJpeg: function _addRarJpeg() {
			var _this = this;

			var el = this.form.rarInput;
			el.onchange = function (e) {
				$del(_this._rjUtil);
				_this._buttonsPlace.insertAdjacentHTML("afterend", "<span><span class=\"de-wait\"></span>" + Lng.wait[lang] + "</span>");
				var myRjUtil = _this._rjUtil = _this._buttonsPlace.nextSibling,
				    file = e.target.files[0];
				readFileArrayBuffer(file).then(function (val) {
					if (_this._rjUtil === myRjUtil) {
						myRjUtil.className = "de-file-rarmsg de-file-utils";
						myRjUtil.title = _this.el.files[0].name + " + " + file.name;
						myRjUtil.textContent = _this.el.files[0].name.replace(/^.+\./, "") + " + " + file.name.replace(/^.+\./, "");
						_this.imgFile = val;
					}
				});
			};
			el.click();
		},
		_changeFilesCount: function _changeFilesCount(val) {
			val = this.form.filesCount + val;
			this.form.filesCount = val < 1 ? 1 : val;
			if (aib.dobr) {
				this.form.fileTd.firstElementChild.value = this.form.filesCount + 1;
			}
		},
		_onFileChange: function _onFileChange() {
			if (Cfg.fileThumb) {
				this._showPviewImage();
			} else {
				this.form.eventFiles(false);
			}
			if (this.empty) {
				this.empty = false;
				this._changeFilesCount(+1);
				$after(this._buttonsPlace, this._delUtil = $new("span", {
					"class": "de-file-del de-file-utils",
					title: Lng.removeFile[lang] }, {
					click: this
				}));
				if (this.form.spoil) {
					$after(this._buttonsPlace, this._spUtil = $new("input", {
						"class": "de-file-spoil de-file-utils",
						type: "checkbox",
						title: Lng.spoilFile[lang] }, {
						click: this
					}));
					this._spUtil.checked = this.form.spoil.checked;
				}
			} else if (this.imgFile) {
				this.imgFile = null;
			}
			if (this.next) {
				if (Cfg.fileThumb) {
					this.next.thumb.style.display = "";
				} else {
					this.next._wrap.style.display = "";
				}
			}
			if (nav.Presto || aib.fch || !/^image\/(?:png|jpeg)$/.test(this.el.files[0].type)) {
				return;
			}
			if (this._rjUtil) {
				$del(this._rjUtil);
				this._rjUtil = null;
			}
			$after(this._buttonsPlace, this._rjUtil = $new("span", {
				"class": "de-file-rar de-file-utils",
				title: Lng.helpAddFile[lang] }, {
				click: this
			}));
		},
		_showPviewImage: function _showPviewImage() {
			var _this = this;

			var files = this.el.files;
			if (!files || !files[0]) {
				return;
			}
			readFileArrayBuffer(files[0]).then(function (val) {
				_this.form.eventFiles(false);
				if (_this.empty) {
					return;
				}
				var file = _this.el.files[0],
				    thumb = _this.thumb;
				thumb.classList.remove("de-file-off");
				thumb = thumb.firstChild.firstChild;
				thumb.title = file.name + ", " + (file.size / 1024).toFixed(2) + "KB";
				thumb.insertAdjacentHTML("afterbegin", file.type === "video/webm" ? "<video class=\"de-file-img\" loop autoplay muted src=\"\"></video>" : "<img class=\"de-file-img\" src=\"\">");
				_this._mediaEl = thumb = thumb.firstChild;
				thumb.src = window.URL.createObjectURL(new Blob([val]));
				thumb = thumb.nextSibling;
				if (thumb) {
					window.URL.revokeObjectURL(thumb.src);
					$del(thumb);
				}
			});
		}
	}, {
		_buttonsPlace: {
			get: function () {
				return Cfg.fileThumb ? this.thumb.firstChild : this.el;
			},
			configurable: true,
			enumerable: true
		},
		_wrap: {
			get: function () {
				return aib.multiFile ? this.el.parentNode : this.el;
			},
			configurable: true,
			enumerable: true
		}
	});

	function FormResizer(dir, el, form) {
		this.dir = dir;
		this.vertical = dir === "top" || dir === "bottom";
		this.qa = form.qArea;
		this.qaStyle = form.qArea.style;
		this.txStyle = form.txta.style;
		el.addEventListener("mousedown", this, false);
	}
	FormResizer.prototype = {
		handleEvent: function handleEvent(e) {
			var val,
			    cr = this.qa.getBoundingClientRect(),
			    maxX = nav.Chrome ? doc.documentElement.clientWidth : Post.sizing.wWidth,
			    maxY = nav.Chrome ? doc.documentElement.clientHeight : Post.sizing.wHeight;
			switch (e.type) {
				case "mousedown":
					switch (this.dir) {
						case "top":
							val = Cfg.qReplyX + "; bottom: " + (maxY - cr.bottom) + "px";break;
						case "bottom":
							val = Cfg.qReplyX + "; top: " + cr.top + "px";break;
						case "left":
							val = "right: " + (maxX - cr.right) + "px; " + Cfg.qReplyY;break;
						case "right":
							val = "left: " + cr.left + "px; " + Cfg.qReplyY;
					}
					this.qaStyle.cssText = val;
					doc.body.addEventListener("mousemove", this, false);
					doc.body.addEventListener("mouseup", this, false);
					$pd(e);
					return;
				case "mousemove":
					if (this.vertical) {
						val = e.clientY;
						this.txStyle.height = Math.max(parseInt(this.txStyle.height, 10) + (this.dir === "top" ? cr.top - (val < 20 ? 0 : val) : (val > maxY - 45 ? maxY - 25 : val) - cr.bottom), 90) + "px";
					} else {
						val = e.clientX;
						this.txStyle.width = Math.max(parseInt(this.txStyle.width, 10) + (this.dir === "left" ? cr.left - (val < 20 ? 0 : val) : (val > maxX - 20 ? maxX : val) - cr.right), 275) + "px";
					}
					return;
				default:
				
					doc.body.removeEventListener("mousemove", this, false);
					doc.body.removeEventListener("mouseup", this, false);
					if (this.vertical) {
						saveCfg("textaHeight", parseInt(this.txStyle.height, 10));
						saveCfg("qReplyY", cr.top < 1 ? "top: 0" : cr.bottom > maxY - 26 ? "bottom: 25px" : "top: " + cr.top + "px");
					} else {
						saveCfg("textaWidth", parseInt(this.txStyle.width, 10));
						saveCfg("qReplyX", cr.left < 1 ? "left: 0" : cr.right > maxX - 1 ? "right: 0" : "left: " + cr.left + "px");
					}
					this.qaStyle.cssText = Cfg.qReplyX + "; " + Cfg.qReplyY;
			}
		}
	};




	function getSubmitError(dc) {
		var err = "";
		if (dc.body.hasChildNodes() && !$q(aib.qDForm, dc)) {
			if (aib.mak) {
				try {
					var json = JSON.parse(dc.body.innerHTML);
					if (json.Status !== "OK" && json.Status !== "Redirect") {
						return Lng.error[lang] + ":\n" + json.Reason;
					} else {
						return null;
					}
				} catch (e) {}
			}
			var els = $Q(aib.qError, dc);
			for (var i = 0, len = els.length; i < len; ++i) {
				err += els[i].innerHTML + "\n";
			}
			err = err.replace(/<a [^>]+>Назад.+|<br.+/, "") || Lng.error[lang] + ":\n" + dc.body.innerHTML;
			err = /:null|successful|uploaded|updating|обновл|удален[о\.]/i.test(err) ? "" : err.replace(/"/g, "'");
		}
		return err;
	}

	var doUploading = async(regeneratorRuntime.mark(function callee$1$3(_ref) {
		var _ref2 = _slicedToArray(_ref, 2);

		var hasFiles = _ref2[0];
		var getProgress = _ref2[1];
		var p, val, beginTime, inited, progress, counterWrap, counterEl, totalEl, speedEl, total, loaded;
		return regeneratorRuntime.wrap(function callee$1$3$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					$alert(Lng.sendingPost[lang] + (hasFiles ? "<br><progress id=\"de-uploadprogress\" value=\"0\" max=\"1\" style=\"display: none; width: 200px;\">" + "</progress><div style=\"display: none; font: bold 12px sans-serif;\">" + "<span></span> / <span></span> (<span></span>)</div>" : ""), "upload", true);

					if (hasFiles) {
						beginTime = Date.now(), inited = false, progress = $id("de-uploadprogress"), counterWrap = progress.nextSibling, counterEl = counterWrap.firstChild, totalEl = counterEl.nextElementSibling, speedEl = totalEl.nextElementSibling;
					}

				case 2:
					if (!(p = getProgress())) {
						context$2$0.next = 19;
						break;
					}

					context$2$0.prev = 3;
					context$2$0.next = 6;
					return p;

				case 6:
					val = context$2$0.sent;
					context$2$0.next = 13;
					break;

				case 9:
					context$2$0.prev = 9;
					context$2$0.t22 = context$2$0["catch"](3);

					$alert(getErrorMessage(context$2$0.t22), "upload", false);
					return context$2$0.abrupt("return");

				case 13:
					if (!val.done) {
						context$2$0.next = 16;
						break;
					}

					checkUpload(val.data);
					return context$2$0.abrupt("return");

				case 16:
					if (hasFiles) {
						if (!inited) {
							total = val.data.total;

							progress.setAttribute("max", total);
							progress.style.display = "";
							totalEl.textContent = prettifySize(total);
							counterWrap.style.display = "";
							inited = true;
						}
						loaded = val.data.loaded;

						progress.value = loaded;
						counterEl.textContent = prettifySize(loaded);
						speedEl.textContent = prettifySize(loaded / (Date.now() - beginTime) * 1000) + "/" + Lng.second[lang];
					}
					context$2$0.next = 2;
					break;

				case 19:
					$alert(Lng.internalError[lang] + getPrettyErrorMessage(new Error()), "upload", false);

				case 20:
				case "end":
					return context$2$0.stop();
			}
		}, callee$1$3, this, [[3, 9]]);
	}));

	function checkUpload(dc) {
		if (aib.krau) {
			pr.form.action = pr.form.action.split("?")[0];
			$id("postform_row_progress").style.display = "none";
			aib.btnZeroLUTime.click();
		}
		updater["continue"]();
		var err = getSubmitError(dc);
		if (err) {
			if (pr.isQuick) {
				pr.setReply(true, false);
			}
			if (/captch|капч|подтвер|verifizie/i.test(err)) {
				pr.refreshCapImg(true);
			}
			$alert(err, "upload", false);
			updater.sendErrNotif();
			return;
		}
		if (Cfg.favOnReply && pr.tNum) {
			pByNum[pr.tNum].thr.setFavorState(true);
		}
		pr.txta.value = "";
		if (pr.file) {
			pr.delFilesUtils();
		}
		if (pr.video) {
			pr.video.value = "";
		}
		Cfg.stats[pr.tNum ? "reply" : "op"]++;
		saveComCfg(aib.dm, Cfg);
		if (!pr.tNum) {
			if (aib.mak) {
				try {
					var json = JSON.parse(dc.body.innerHTML);
					if (json.Status === "Redirect") {
						window.location = aib.getThrdUrl(aib.b, json.Target);
					}
				} catch (e) {}
			} else {
				window.location = aib.getThrdUrl(aib.b, aib.getTNum($q(aib.qDForm, dc)));
			}
			return;
		}
		var el = !aib.tiny && !aib.kus && (aib.qPostRedir === null || $q(aib.qPostRedir, dc)) ? $q(aib.qDForm, dc) : null;
		if (aib.t) {
			dForm.firstThr.clearPostsMarks();
			if (el) {
				dForm.firstThr.loadNewFromForm(el);
				if (Cfg.scrAfterRep) {
					scrollTo(0, window.pageYOffset + dForm.firstThr.last.el.getBoundingClientRect().top);
				}
				closeAlert($id("de-alert-upload"));
			} else {
				dForm.firstThr.loadNew(true).then(function () {
					return AjaxError.Success;
				}, function (e) {
					return e;
				}).then(function (e) {
					infoLoadErrors(e);
					if (Cfg.scrAfterRep) {
						scrollTo(0, window.pageYOffset + dForm.firstThr.last.el.getBoundingClientRect().top);
					}
					closeAlert($id("de-alert-upload"));
				});
			}
		} else {
			if (el) {
				pByNum[pr.tNum].thr.loadFromForm(visPosts, true, el);
				closeAlert($id("de-alert-upload"));
			} else {
				pByNum[pr.tNum].thr.load(visPosts, false, false).then(function () {
					return closeAlert($id("de-alert-upload"));
				});
			}
		}
		pr.closeQReply();
		pr.refreshCapImg(false);
		pr.filesCount = 0;
	}

	var checkDelete = async(regeneratorRuntime.mark(function callee$1$4(dc) {
		var err, _ref, _ref2, num, post, els, threads, isThr, i, len, el, _iteratorNormalCompletion, _didIteratorError, _iteratorError, _iterator, _step, thr;

		return regeneratorRuntime.wrap(function callee$1$4$(context$2$0) {
			while (1) switch (context$2$0.prev = context$2$0.next) {
				case 0:
					err = getSubmitError(dc);

					if (!err) {
						context$2$0.next = 5;
						break;
					}

					$alert(Lng.errDelete[lang] + err, "delete", false);
					updater.sendErrNotif();
					return context$2$0.abrupt("return");

				case 5:
					_ref = doc.location.hash.match(/\d+/) || [];
					_ref2 = _slicedToArray(_ref, 1);
					num = _ref2[0];

					if (num) {
						post = pByNum[num];

						if (post) {
							if (!post.isOp) {
								post.el.className = aib.cReply;
							}
							doc.location.hash = "";
						}
					}
					els = $Q(aib.qRPost + " input:checked", dForm.el), threads = new Set(), isThr = aib.t;

					for (i = 0, len = els.length; i < len; ++i) {
						el = els[i];

						el.checked = false;
						if (!isThr) {
							threads.add(aib.getPostEl(el).post.thr);
						}
					}

					if (!isThr) {
						context$2$0.next = 23;
						break;
					}

					dForm.firstThr.clearPostsMarks();
					context$2$0.prev = 13;
					context$2$0.next = 16;
					return dForm.firstThr.loadNew(false);

				case 16:
					context$2$0.next = 21;
					break;

				case 18:
					context$2$0.prev = 18;
					context$2$0.t23 = context$2$0["catch"](13);

					infoLoadErrors(context$2$0.t23);

				case 21:
					context$2$0.next = 49;
					break;

				case 23:
					_iteratorNormalCompletion = true;
					_didIteratorError = false;
					_iteratorError = undefined;
					context$2$0.prev = 26;
					_iterator = threads[Symbol.iterator]();

				case 28:
					if (_iteratorNormalCompletion = (_step = _iterator.next()).done) {
						context$2$0.next = 35;
						break;
					}

					thr = _step.value;
					context$2$0.next = 32;
					return thr.load(visPosts, false, false);

				case 32:
					_iteratorNormalCompletion = true;
					context$2$0.next = 28;
					break;

				case 35:
					context$2$0.next = 41;
					break;

				case 37:
					context$2$0.prev = 37;
					context$2$0.t24 = context$2$0["catch"](26);
					_didIteratorError = true;
					_iteratorError = context$2$0.t24;

				case 41:
					context$2$0.prev = 41;
					context$2$0.prev = 42;

					if (!_iteratorNormalCompletion && _iterator["return"]) {
						_iterator["return"]();
					}

				case 44:
					context$2$0.prev = 44;

					if (!_didIteratorError) {
						context$2$0.next = 47;
						break;
					}

					throw _iteratorError;

				case 47:
					return context$2$0.finish(44);

				case 48:
					return context$2$0.finish(41);

				case 49:
					$alert(Lng.succDeleted[lang], "delete", false);

				case 50:
				case "end":
					return context$2$0.stop();
			}
		}, callee$1$4, this, [[13, 18], [26, 37, 41, 49], [42,, 44, 48]]);
	}));

	function readFileArrayBuffer(file) {
		return new Promise(function (resolve, reject) {
			var fr = new FileReader();
			fr.onload = function (e) {
				return resolve(e.target.result);
			};
			fr.readAsArrayBuffer(file);
		});
	}

	function cleanFile(data, extraData) {
		var i,
		    len,
		    val,
		    lIdx,
		    jpgDat,
		    img = nav.getUnsafeUint8Array(data),
		    rand = Cfg.postSameImg && String(Math.round(Math.random() * 1000000)),
		    rExif = !!Cfg.removeEXIF,
		    rv = extraData ? rand ? [img, extraData, rand] : [img, extraData] : rand ? [img, rand] : [img];
		if (!rand && !rExif && !extraData) {
			return rv;
		}
	
		if (img[0] === 255 && img[1] === 216) {
			var deep = 1;
			for (i = 2, len = img.length - 1, val = [null, null], lIdx = 2, jpgDat = null; i < len;) {
				if (img[i] === 255) {
					if (rExif) {
						if (!jpgDat && deep === 1) {
							if (img[i + 1] === 225 && img[i + 4] === 69) {
								jpgDat = readExif(data, i + 10, (img[i + 2] << 8) + img[i + 3]);
							} else if (img[i + 1] === 224 && img[i + 7] === 70 && (img[i + 2] !== 0 || img[i + 3] >= 14 || img[i + 15] !== 255)) {
								jpgDat = img.subarray(i + 11, i + 16);
							}
						}
						if (img[i + 1] >> 4 === 14 && img[i + 1] !== 238 || img[i + 1] === 254) {
							if (lIdx !== i) {
								val.push(img.subarray(lIdx, i));
							}
							i += 2 + (img[i + 2] << 8) + img[i + 3];
							lIdx = i;
							continue;
						}
					} else if (img[i + 1] === 216) {
						deep++;
						i++;
						continue;
					}
					if (img[i + 1] === 217 && --deep === 0) {
						break;
					}
				}
				i++;
			}
			i += 2;
			if (!extraData && len - i > 75) {
				i = len;
			}
			if (lIdx === 2) {
				if (i !== len) {
					rv[0] = nav.getUnsafeUint8Array(data, 0, i);
				}
				return rv;
			}
			val[0] = new Uint8Array([255, 216, 255, 224, 0, 14, 74, 70, 73, 70, 0, 1, 1]);
			val[1] = jpgDat || new Uint8Array([0, 0, 1, 0, 1]);
			val.push(img.subarray(lIdx, i));
			if (extraData) {
				val.push(extraData);
			}
			if (rand) {
				val.push(rand);
			}
			return val;
		}
	
		if (img[0] === 137 && img[1] === 80) {
			for (i = 0, len = img.length - 7; i < len && (img[i] !== 73 || img[i + 1] !== 69 || img[i + 2] !== 78 || img[i + 3] !== 68); i++) {}
			i += 8;
			if (i !== len && (extraData || len - i <= 75)) {
				rv[0] = nav.getUnsafeUint8Array(data, 0, i);
			}
			return rv;
		}
	
		if (img[0] === 26 && img[1] === 69 && img[2] === 223 && img[3] === 163) {
			return new WebmParser(data).addData(rand).getData();
		}
		return null;
	}

	function readExif(data, off, len) {
		var i,
		    xRes = 0,
		    yRes = 0,
		    resT = 0,
		    dv = nav.getUnsafeDataView(data, off),
		    le = String.fromCharCode(dv.getUint8(0), dv.getUint8(1)) !== "MM";
		if (dv.getUint16(2, le) !== 42) {
			return null;
		}
		i = dv.getUint32(4, le);
		if (i > len) {
			return null;
		}
		for (var j = 0, tgLen = dv.getUint16(i, le); j < tgLen; j++) {
			var dE = i + 2 + 12 * j,
			    tag = dv.getUint16(dE, le);
			if (tag === 296) {
				resT = dv.getUint16(dE + 8, le) - 1;
			} else if (tag === 282 || tag === 283) {
				dE = dv.getUint32(dE + 8, le);
				if (dE > len) {
					return null;
				}
				if (tag === 282) {
					xRes = Math.round(dv.getUint32(dE, le) / dv.getUint32(dE + 4, le));
				} else {
					yRes = Math.round(dv.getUint32(dE, le) / dv.getUint32(dE + 4, le));
				}
			}
		}
		xRes = xRes || yRes;
		yRes = yRes || xRes;
		return new Uint8Array([resT & 255, xRes >> 8, xRes & 255, yRes >> 8, yRes & 255]);
	}

	WebmParser = function (data) {
		var EBMLId = 440786851,
		    segmentId = 408125543,
		    voidId = 236;
		function WebmElement(elData, dataLength, offset) {
			if (offset + 4 >= dataLength) {
				return;
			}
			var num = elData.getUint32(offset),
			    clz = Math.clz32(num);
			if (clz > 3) {
				this.error = true;
				return;
			}
			offset += clz + 1;
			if (offset + 4 >= dataLength) {
				this.error = true;
				return;
			}
			var id = num >>> 8 * (3 - clz),
			    headSize = clz + 1;
			num = elData.getUint32(offset);
			clz = Math.clz32(num);
			if (clz > 3) {
				if ((num & 4294967295 >>> clz + 1) !== 0) {
					this.error = true;
					return;
				}
				if (offset + 8 >= dataLength) {
					this.error = true;
					return;
				}
				headSize += 4;
				offset += 4;
				num = elData.getUint32(offset);
				clz -= 4;
			}
			var size = num >>> 8 * (3 - clz);
			headSize += clz + 1;
			offset += clz + 1;
			if (offset + size > dataLength) {
				this.error = true;
				return;
			}
			this.data = elData;
			this.offset = offset;
			this.endOffset = offset + size;
			this.id = id;
			this.headSize = headSize;
			this.size = size;
		}
		WebmElement.prototype = {
			error: false,
			id: 0
		};

		function Parser(data) {
			var dv = nav.getUnsafeDataView(data),
			    len = dv.byteLength,
			    el = new WebmElement(dv, len, 0),
			    offset = 0,
			    voids = [];
			error: do {
				if (el.error || el.id !== EBMLId) {
					break;
				}
				this.EBML = el;
				offset += el.headSize + el.size;
				while (true) {
					el = new WebmElement(dv, len, offset);
					if (el.error) {
						break error;
					}
					if (el.id === segmentId) {
						this.segment = el;
						break;
					} else if (el.id === voidId) {
						voids.push(el);
					} else {
						break error;
					}
					offset += el.headSize + el.size;
				}
				this.voids = voids;
				this.data = data;
				this.length = len;
				this.rv = [null];
				this.error = false;
				return;
			} while (false);
			this.error = true;
		}
		Parser.prototype = {
			addData: function addData(data) {
				if (this.error || !data) {
					return this;
				}
				var size = typeof data === "string" ? data.length : data.byteLength;
				if (size > 127) {
					this.error = true;
					return;
				}
				this.rv.push(new Uint8Array([voidId, 128 | size]), data);
				return this;
			},
			getData: function getData() {
				if (this.error) {
					return null;
				}
				this.rv[0] = nav.getUnsafeUint8Array(this.data, 0, this.segment.endOffset);
				return this.rv;
			}
		};

		WebmParser = Parser;
		return new Parser(data);
	};




	function genImgHash(data) {
		var buf = new Uint8Array(data[0]),
		    oldw = data[1],
		    oldh = data[2],
		    size = oldw * oldh;
		for (var i = 0, j = 0; i < size; i++, j += 4) {
			buf[i] = buf[j] * 0.3 + buf[j + 1] * 0.59 + buf[j + 2] * 0.11;
		}
		var newh = 8,
		    neww = 8,
		    levels = 3,
		    areas = 256 / levels,
		    values = 256 / (levels - 1),
		    hash = 0;
		for (var i = 0; i < newh; i++) {
			for (var j = 0; j < neww; j++) {
				var tmp = i / (newh - 1) * (oldh - 1),
				    l = Math.min(tmp | 0, oldh - 2),
				    u = tmp - l;
				tmp = j / (neww - 1) * (oldw - 1);
				var c = Math.min(tmp | 0, oldw - 2),
				    t = tmp - c;
				hash = (hash << 4) + Math.min(values * ((buf[l * oldw + c] * ((1 - t) * (1 - u)) + buf[l * oldw + c + 1] * (t * (1 - u)) + buf[(l + 1) * oldw + c + 1] * (t * u) + buf[(l + 1) * oldw + c] * ((1 - t) * u)) / areas | 0), 255);
				var g = hash & 4026531840;
				if (g) {
					hash ^= g >>> 24;
				}
				hash &= ~g;
			}
		}
		return { hash: hash };
	}
	function ImgBtnsShowHider(nextFn, prevFn) {
		dForm.el.insertAdjacentHTML("beforeend", "<div style=\"display: none;\">" + "<div id=\"de-img-btn-next\" de-title=\"" + Lng.nextImg[lang] + "\"><div></div></div>" + "<div id=\"de-img-btn-prev\" de-title=\"" + Lng.prevImg[lang] + "\"><div></div></div></div>");
		var btns = dForm.el.lastChild;
		this._btns = btns;
		this._btnsStyle = btns.style;
		this._nextFn = nextFn;
		this._prevFn = prevFn;
		doc.defaultView.addEventListener("mousemove", this, false);
		btns.addEventListener("mouseover", this, false);
	}
	ImgBtnsShowHider.prototype = {
		handleEvent: function handleEvent(e) {
			switch (e.type) {
				case "mousemove":
					var curX = e.clientX,
					    curY = e.clientY;
					if (this._oldX !== curX || this._oldY !== curY) {
						this._oldX = curX;
						this._oldY = curY;
						this.show();
					}
					return;
				case "mouseover":
					if (!this.hasEvents) {
						this.hasEvents = true;
						this._btns.addEventListener("mouseout", this, false);
						this._btns.addEventListener("click", this, false);
					}
					if (!this._hidden) {
						clearTimeout(this._hideTmt);
						KeyEditListener.setTitle(this._btns.firstChild, 17);
						KeyEditListener.setTitle(this._btns.lastChild, 4);
					}
					return;
				case "mouseout":
					this._setHideTmt();return;
				case "click":
					switch (e.target.parentNode.id) {
						case "de-img-btn-next":
							this._nextFn();return;
						case "de-img-btn-prev":
							this._prevFn();
					}
			}
		},
		hide: function hide() {
			this._btnsStyle.display = "none";
			this._hidden = true;
			this._oldX = this._oldY = -1;
		},
		remove: function remove() {
			$del(this._btns);
			doc.defaultView.removeEventListener("mousemove", this, false);
			clearTimeout(this._hideTmt);
		},
		show: function show() {
			if (this._hidden) {
				this._btnsStyle.display = "";
				this._hidden = false;
				this._setHideTmt();
			}
		},

		_hasEvents: false,
		_hideTmt: 0,
		_hidden: true,
		_oldX: -1,
		_oldY: -1,
		_setHideTmt: function _setHideTmt() {
			clearTimeout(this._hideTmt);
			this._hideTmt = setTimeout(this.hide.bind(this), 2000);
		}
	};

	function AttachmentViewer(data) {
		this._show(data);
	}
	AttachmentViewer.prototype = Object.defineProperties({
		data: null,
		close: function close(e) {
			if (this.data.inPview && this.data.post.sticked) {
				$c("de-btn-stick-on", this.data.post.el).click();
			}
			if (this.hasOwnProperty("_btns")) {
				this._btns.remove();
			}
			this._remove(e);
		},
		handleEvent: function handleEvent(e) {
			var isOverEvent = false;
			switch (e.type) {
				case "mousedown":
					if (this.data.isVideo && this.data.isControlClick(e, this._elStyle.height)) {
						return;
					}
					this._oldX = e.clientX;
					this._oldY = e.clientY;
					doc.body.addEventListener("mousemove", this, true);
					doc.body.addEventListener("mouseup", this, true);
					break;
				case "mousemove":
					var curX = e.clientX,
					    curY = e.clientY;
					if (curX !== this._oldX || curY !== this._oldY) {
						this._elStyle.left = (this._oldL = parseInt(this._elStyle.left, 10) + curX - this._oldX) + "px";
						this._elStyle.top = (this._oldT = parseInt(this._elStyle.top, 10) + curY - this._oldY) + "px";
						this._oldX = curX;
						this._oldY = curY;
						this._moved = true;
					}
					return;
				case "mouseup":
					doc.body.removeEventListener("mousemove", this, true);
					doc.body.removeEventListener("mouseup", this, true);
					return;
				case "click":
					if (this.data.isVideo && this.data.isControlClick(e, this._elStyle.height)) {
						return;
					}
					if (e.button === 0) {
						if (this._moved) {
							this._moved = false;
						} else {
							this.close(e);
							Attachment.viewer = null;
						}
						e.stopPropagation();
						break;
					}
					return;
				case "mouseover":
					isOverEvent = true;
								case "mouseout":
					var temp = e.relatedTarget;
					if (!temp || temp !== this._obj && !this._obj.contains(temp)) {
						if (isOverEvent) {
							Pview.mouseEnter(this.data.post);
						} else if (Pview.top && this.data.post.el !== temp && !this.data.post.el.contains(temp)) {
							Pview.top.markToDel();
						}
					}
					return;
				case "mousewheel":
					this._handleWheelEvent(e.clientX, e.clientY, -1 / 40 * ("wheelDeltaY" in e ? e.wheelDeltaY : e.wheelDelta));
					break;
				default:
				
					this._handleWheelEvent(e.clientX, e.clientY, e.deltaY);
			}
			$pd(e);
		},
		navigate: function navigate(isForward) {
			var data = this.data;
			do {
				data = data.getFollow(isForward);
			} while (!data.isVideo && !data.isImage);
			this.update(data, true, null);
		},
		update: function update(data, showButtons, e) {
			this._remove(e);
			this._show(data, showButtons);
		},

		_ar: 0,
		_data: null,
		_elStyle: null,
		_fullEl: null,
		_obj: null,
		_oldL: 0,
		_oldT: 0,
		_curH: 0,
		_curW: 0,
		_oldX: 0,
		_oldY: 0,
		_maxSize: null,
		_minSize: 0,
		_moved: false,
		_getHolder: function _getHolder(el, data) {
			var screenWidth = Post.sizing.wWidth,
			    screenHeight = Post.sizing.wHeight,
			    minSize = Cfg.minImgSize,
			    size = data.computeFullSize(false),
			    ar = size[0] / size[1];
			this._ar = ar;
			this._curW = size[0];
			this._curH = size[1];
			if (minSize * ar > screenWidth) {
				minSize = Math.floor(screenWidth / ar);
			}
			if (minSize / ar > screenHeight) {
				minSize = Math.floor(screenHeight * ar);
			}
			this._minSize = minSize;
			this._maxSize = !size[2] ? null : minSize > (size[2][2] > 1 ? size[2][0] : size[2][1]) ? size[2] : null;
			this._oldL = (screenWidth - size[0]) / 2 - 1;
			this._oldT = (screenHeight - size[1]) / 2 - 1;
			var obj = $add("<div class=\"de-img-center\" style=\"top:" + this._oldT + "px; left:" + this._oldL + "px; width:" + size[0] + "px; height:" + size[1] + "px; display: block\"></div>");
			if (data.isImage) {
				obj.insertAdjacentHTML("afterbegin", "<a href=\"" + data.src + "\"></a>");
				obj.firstChild.appendChild(el);
			} else {
				obj.appendChild(el);
			}
			return obj;
		},
		_handleWheelEvent: function _handleWheelEvent(clientX, clientY, delta) {
			if (delta === 0) {
				return;
			}
			var oldW = this._curW,
			    oldH = this._curH,
			    width = delta < 0 ? oldW * this._zoomFactor : oldW / this._zoomFactor,
			    height = delta < 0 ? oldH * this._zoomFactor : oldH / this._zoomFactor;
			if (delta > 0) {
				var size = resizeImage([width, height, this._ar], this._minSize, this._maxSize);
				width = size[0];
				height = size[1];
			}
			this._curW = width;
			this._curH = height;
			this._elStyle.width = width + "px";
			this._elStyle.height = height + "px";
			this._elStyle.left = (this._oldL = parseInt(clientX - width / oldW * (clientX - this._oldL), 10)) + "px";
			this._elStyle.top = (this._oldT = parseInt(clientY - height / oldH * (clientY - this._oldT), 10)) + "px";
		},
		_show: function _show(data) {
			var el = data.getFullObject(),
			    obj = this._getHolder(el, data);
			this._elStyle = obj.style;
			this.data = data;
			this._fullEl = el;
			this._obj = obj;
			if ("onwheel" in obj) {
				obj.addEventListener("wheel", this, true);
			} else {
				obj.addEventListener("mousewheel", this, true);
			}
			obj.addEventListener("mousedown", this, true);
			obj.addEventListener("click", this, true);
			if (data.inPview) {
				obj.addEventListener("mouseover", this, true);
				obj.addEventListener("mouseout", this, true);
				if (!data.post.sticked) {
					$c("de-btn-stick", data.post.el).click();
				}
			}
			if (!data.inPview) {
				this._btns.show();
			} else if (this.hasOwnProperty("_btns")) {
				this._btns.hide();
			}
			dForm.el.appendChild(obj);
		},
		_remove: function _remove(e) {
			if (this.data.isVideo && this._fullEl.tagName === "VIDEO") {
				this._fullEl.pause();
				this._fullEl.src = "";
			}
			this._obj.style.display = "none";
			setTimeout($del, 100, this._obj);
			if (e && this.data.inPview) {
				this.data.sendCloseEvent(e, false);
			}
		}
	}, {
		_btns: {
			get: function () {
				var val = new ImgBtnsShowHider(this.navigate.bind(this, true), this.navigate.bind(this, false));
				Object.defineProperty(this, "_btns", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		_zoomFactor: {
			get: function () {
				var val = 1 + Cfg.zoomFactor / 100;
				Object.defineProperty(this, "_zoomFactor", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		}
	});

	function IAttachmentData() {}
	IAttachmentData.prototype = Object.defineProperties({
		expanded: false,
		collapse: function collapse(e) {
			if (!this.isVideo || !this.isControlClick(e, this._fullEl.style.height)) {
				this.expanded = false;
				$del(this._fullEl);
				this._fullEl = null;
				this.el.parentNode.style.display = "";
				$del((aib.hasPicWrap ? this._getImageParent() : this.el.parentNode).nextSibling);
				if (e && this.inPview) {
					this.sendCloseEvent(e, true);
				}
				return true;
			}
			return false;
		},
		computeFullSize: function computeFullSize(inPost) {
			var maxSize,
			    width = this.width,
			    height = this.height;
			if (Cfg.resizeDPI) {
				width /= Post.sizing.dPxRatio;
				height /= Post.sizing.dPxRatio;
			}
			if (Cfg.resizeImgs) {
				if (inPost) {
					maxSize = [Post.sizing.wWidth - this._offset - 3, Number.MAX_SAFE_INTEGER, 0];
				} else {
					var maxWidth = Post.sizing.wWidth - 2,
					    maxHeight = Post.sizing.wHeight - 2;
					maxSize = [maxWidth, maxHeight, maxWidth / maxHeight];
				}
			} else {
				maxSize = null;
			}
			var size = resizeImage([width, height, width / height], Cfg.minImgSize, maxSize);
			return [size[0], size[1], maxSize];
		},
		expand: function expand(inPost, e) {
			if (!inPost) {
				if (Attachment.viewer) {
					if (Attachment.viewer.data === this) {
						Attachment.viewer.close(e);
						Attachment.viewer = null;
						return;
					}
					Attachment.viewer.update(this, e);
				} else {
					Attachment.viewer = new AttachmentViewer(this);
				}
				return;
			}
			this.expanded = true;
			var el = this.el,
			    size = this.computeFullSize(inPost);
			(aib.hasPicWrap ? this._getImageParent() : el.parentNode).insertAdjacentHTML("afterend", "<div class=\"de-after-fimg\"></div>");
			el.parentNode.style.display = "none";
			this._fullEl = this.getFullObject();
			this._fullEl.className = "de-img-full";
			this._fullEl.style.width = size[0] + "px";
			this._fullEl.style.height = size[1] + "px";
			$after(el.parentNode, this._fullEl);
		},
		getFollowPost: function getFollowPost(isForward) {
			var imgs,
			    post = this.post;
			do {
				post = post.getAdjacentVisPost(!isForward);
				if (!post) {
					post = isForward ? dForm.firstThr.op : dForm.lastThr.last;
					if (post.hidden || post.thr.hidden) {
						post = post.getAdjacentVisPost(!isForward);
					}
				}
				imgs = post.images;
			} while (!imgs.length);
			return isForward ? imgs.first : imgs.last;
		},
		getFullObject: function getFullObject() {
			var obj,
			    src = this.src;
			if (this.isVideo) {
				if (aib.tiny) {
					src = src.replace(/^.*?\?v=|&.*?$/g, "");
				}
				if (nav.canPlayWebm) {
					obj = $add("<video style=\"width: 100%; height: 100%\" src=\"" + src + "\" loop autoplay " + (Cfg.webmControl ? "controls " : "") + (Cfg.webmVolume === 0 ? "muted " : "") + "></video>");
					if (Cfg.webmVolume !== 0) {
						obj.oncanplay = function () {
							this.volume = Cfg.webmVolume / 100;
						};
					}
					obj.onerror = function () {
						if (!this.onceLoaded) {
							this.load();
							this.onceLoaded = true;
						}
					};
					obj.onvolumechange = function () {
						saveCfg("webmVolume", Math.round(this.volume * 100));
					};
				} else {
					obj = $add("<object style=\"width: 100%; height: 100%\" data=\"" + src + "\" type=\"application/x-vlc-plugin\">" + "<param name=\"pluginspage\" value=\"http://www.videolan.org/vlc/\" />" + "<param name=\"controls\" value=\"" + (Cfg.webmControl ? "true" : "false") + "\" />" + "<param name=\"loop\" value=\"true\" />" + "<param name=\"autoplay\" value=\"true\" />" + "<param name=\"wmode\" value=\"transparent\" /></object>");
				}
			} else {
				obj = $add("<img style=\"width: 100%; height: 100%\" src=\"" + src + "\" alt=\"" + src + "\"></a>");
				obj.onload = obj.onerror = function (e) {
					if (this.naturalHeight + this.naturalWidth === 0 && !this.onceLoaded) {
						this.src = this.src;
						this.onceLoaded = true;
					}
				};
			}
			return obj;
		},
		isControlClick: function isControlClick(e, styleHeight) {
			return Cfg.webmControl && e.clientY > e.target.getBoundingClientRect().top + parseInt(styleHeight, 10) - 30;
		},
		sendCloseEvent: function sendCloseEvent(e, inPost) {
			var pv = this.post,
			    cr = pv.el.getBoundingClientRect(),
			    x = e.pageX - window.pageXOffset,
			    y = e.pageY - window.pageYOffset;
			if (!inPost) {
				while (x > cr.right || x < cr.left || y > cr.bottom || y < cr.top) {
					pv = pv.parent;
					if (pv) {
						cr = pv.el.getBoundingClientRect();
					} else {
						if (Pview.top) {
							Pview.top.markToDel();
						}
						return;
					}
				}
				if (pv.kid) {
					pv.kid.markToDel();
				} else {
					clearTimeout(Pview.delTO);
				}
			} else if (x > cr.right || y > cr.bottom && Pview.top) {
				Pview.top.markToDel();
			}
		},

		_fullEl: null }, {
		inPview: {
			get: function () {
				var val = this.post.isPview;
				Object.defineProperty(this, "inPview", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		isImage: {
			get: function () {
				var val = /\.jpe?g|\.png|\.gif/i.test(this.src) || this.src.startsWith("blob:") && !this.el.hasAttribute("de-video");
				Object.defineProperty(this, "isImage", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		isVideo: {
			get: function () {
				var val = /\.webm(?:&|$)/i.test(this.src) || this.src.startsWith("blob:") && this.el.hasAttribute("de-video");
				Object.defineProperty(this, "isVideo", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		height: {
			get: function () {
				var dat = this._getImageSize();
				Object.defineProperties(this, {
					width: { value: dat[0] },
					height: { value: dat[1] }
				});
				return dat[1];
			},
			configurable: true,
			enumerable: true
		},
		src: {
			get: function () {
				var val = this._getImageSrc();
				Object.defineProperty(this, "src", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		width: {
			get: function () {
				var dat = this._getImageSize();
				Object.defineProperties(this, {
					width: { value: dat[0] },
					height: { value: dat[1] }
				});
				return dat[0];
			},
			configurable: true,
			enumerable: true
		},
		_offset: {
			get: function () {
				var val;
				if (this.post.hidden) {
					this.post.hideContent(false);
					val = this.el.getBoundingClientRect().left + window.pageXOffset;
					this.post.hideContent(true);
				} else {
					val = this.el.getBoundingClientRect().left + window.pageXOffset;
				}
				Object.defineProperty(this, "_offset", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		}
	});

	function EmbeddedImage(post, el, idx) {
		this.post = post;
		this.el = el;
		this.idx = idx;
	}
	EmbeddedImage.prototype = Object.create(IAttachmentData.prototype, {
		getFollow: { value: function value(isForward) {
				var nImage = this.post.images.data[isForward ? this.idx + 1 : this.idx - 1];
				return nImage ? nImage : this.getFollowPost(isForward);
			} },
		_useCache: { value: false },
		_getImageSize: { value: function value() {
				var iEl = new Image();
				iEl.src = this.el.src;
				return [iEl.width, iEl.height];
			} },
		_getImageSrc: { value: function value() {
				return this.el.src;
			} },
		_getImageParent: { value: function value() {
				return this.el.parentNode;
			} }
	});

	function Attachment(post, el, idx) {
		this.post = post;
		this.el = el;
		this.idx = idx;
	}
	Attachment.cachedOffset = -1;
	Attachment.viewer = null;
	Attachment.prototype = Object.create(IAttachmentData.prototype, {
		info: { configurable: true, get: function get() {
				var val = aib.getFileInfo(aib.getImgWrap(this.el.parentNode));
				Object.defineProperty(this, "info", { value: val });
				return val;
			} },
		weight: { configurable: true, get: function get() {
				var val = 0;
				if (this.info) {
					var w = this.info.match(/(\d+(?:[\.,]\d+)?)\s*([mkк])?i?[bб]/i);
					val = w[2] === "M" ? w[1] * 1000 | 0 : !w[2] ? Math.round(w[1] / 1000) : w[1];
				}
				Object.defineProperty(this, "weight", { value: val });
				return val;
			} },
		getFollow: { value: function value(isForward) {
				var nImage = this.post.images.data[isForward ? this.idx + 1 : this.idx - 1];
				return nImage ? nImage : this.getFollowPost(isForward);
			} },

		_offset: { configurable: true, get: function get() {
				var val = Attachment.cachedOffset;
				if (val !== -1) {
					Object.defineProperty(this, "_offset", { value: val });
				} else {
					val = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(Object.getPrototypeOf(this)), "_offset").get.call(this);
					if (!this.inPview && !this.post.isOp && !this.post.prev.omitted && !this.post.prev.isOp && this.post.count > 4) {
						Attachment.cachedOffset = val;
					}
				}
				return val;
			} },
		_getImageSize: { value: function value() {
				if (this.info) {
					var size = this.info.match(/(\d+)\s?[x\u00D7]\s?(\d+)/);
					return [size[1], size[2]];
				}
				return [-1, -1];
			} },
		_getImageSrc: { value: function value() {
				return aib.getImgLink(this.el).href;
			} },
		_getImageParent: { value: function value() {
				return aib.getImgParent(this.el.parentNode);
			} }
	});

	var ImagesHashStorage = Object.create(Object.defineProperties({
		endFn: function endFn() {
			if (this.hasOwnProperty("_storage")) {
				sesStorage["de-imageshash"] = JSON.stringify(this._storage);
			}
			if (this.hasOwnProperty("_workers")) {
				this._workers.clear();
				delete this._workers;
			}
		},

		_getHashHelper: regeneratorRuntime.mark(function _getHashHelper(imgObj) {
			var _this = this;

			var el, src, data, buffer, val, w, h, imgData, cnv, ctx;
			return regeneratorRuntime.wrap(function _getHashHelper$(context$2$0) {
				while (1) switch (context$2$0.prev = context$2$0.next) {
					case 0:
						el = imgObj.el, src = imgObj.src;

						if (!(src in _this._storage)) {
							context$2$0.next = 3;
							break;
						}

						return context$2$0.abrupt("return", _this._storage[src]);

					case 3:
						if (el.complete) {
							context$2$0.next = 6;
							break;
						}

						context$2$0.next = 6;
						return new Promise(function (resolve) {
							return el.addEventListener("load", function () {
								return resolve();
							});
						});

					case 6:
						if (!(el.naturalWidth + el.naturalHeight === 0)) {
							context$2$0.next = 8;
							break;
						}

						return context$2$0.abrupt("return", -1);

					case 8:
						val = -1, w = el.naturalWidth, h = el.naturalHeight;

						if (!aib.fch) {
							context$2$0.next = 16;
							break;
						}

						context$2$0.next = 12;
						return downloadImgData(el.src);

					case 12:
						imgData = context$2$0.sent;

						if (imgData) {
							buffer = imgData.buffer;
						}
						context$2$0.next = 22;
						break;

					case 16:
						cnv = _this._canvas;

						cnv.width = w;
						cnv.height = h;
						ctx = cnv.getContext("2d");

						ctx.drawImage(el, 0, 0);
						buffer = ctx.getImageData(0, 0, w, h).data.buffer;

					case 22:
						if (!buffer) {
							context$2$0.next = 27;
							break;
						}

						context$2$0.next = 25;
						return new Promise(function (resolve) {
							return _this._workers.run([buffer, w, h], [buffer], function (val) {
								return resolve(val);
							});
						});

					case 25:
						data = context$2$0.sent;

						if (data && "hash" in data) {
							val = data.hash;
						}

					case 27:
						_this._storage[src] = val;
						return context$2$0.abrupt("return", val);

					case 29:
					case "end":
						return context$2$0.stop();
				}
			}, _getHashHelper, this);
		}) }, {
		getHash: {
			get: function () {
				var val = this._getHashHelper.bind(this);
				Object.defineProperty(this, "getHash", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		_canvas: {
			get: function () {
				var val = doc.createElement("canvas");
				Object.defineProperty(this, "_canvas", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		_storage: {
			get: function () {
				var val = null;
				try {
					val = JSON.parse(sesStorage["de-imageshash"]);
				} finally {
					if (!val) {
						val = {};
					}
					Object.defineProperty(this, "_storage", { value: val });
					return val;
				}
			},
			configurable: true,
			enumerable: true
		},
		_workers: {
			get: function () {
				var val = new WorkerPool(4, genImgHash, emptyFn);
				Object.defineProperty(this, "_workers", { value: val, configurable: true });
				return val;
			},
			configurable: true,
			enumerable: true
		}
	}));

	function processImageNames(el) {
		var addSrc = Cfg.imgSrcBtns,
		    delNames = Cfg.delImgNames;
		if (!addSrc && !delNames) {
			return;
		}
		for (var i = 0, els = $Q(aib.qImgLink, el), len = els.length; i < len; i++) {
			var link = els[i];
			if (/google\.|tineye\.com|iqdb\.org/.test(link.href)) {
				$del(link);
				continue;
			}
			if (link.firstElementChild) {
				continue;
			}
			if (addSrc) {
				link.insertAdjacentHTML("beforebegin", "<span class=\"de-btn-src\" de-menu=\"imgsrc\"></span>");
			}
			if (delNames) {
				link.classList.add("de-img-name");
				link.textContent = link.textContent.split(".").slice(-1)[0];
			}
		}
	}

	function embedImagesLinks(el) {
		for (var i = 0, els = $Q(aib.qMsgImgLink, el), len = els.length; i < len; ++i) {
			var link = els[i];
			if (link.parentNode.tagName === "SMALL") {
				return;
			}
			var a = link.cloneNode(false);
			a.target = "_blank";
			a.innerHTML = "<img class=\"de-img-pre\" src=\"" + a.href + "\">";
			$before(link, a);
		}
	}




	function Post(el, thr, num, count, isOp, prev, isLight) {
		this.count = count;
		this.el = el;
		this.isOp = isOp;
		this.num = num;
		this.thr = thr;
		this.prev = prev;
		if (prev) {
			prev.next = this;
		}
		if (isLight) {
			return;
		}
		var refEl = $q(aib.qRef, el),
		    html = "<span class=\"de-post-btns" + (isOp ? "" : " de-post-counter") + "\"><span class=\"de-btn-hide\" de-menu=\"hide\"></span><span class=\"de-btn-rep\"></span>";
		this._pref = refEl;
		this.ref = [];
		el.post = this;
		if (isOp) {
			if (!aib.t) {
				html += "<span class=\"de-btn-expthr\" de-menu=\"expand\"></span>";
			}
			html += "<span class=\"de-btn-fav\" title=\"" + Lng.addFav[lang] + "\"></span>";
		}
		this.sage = aib.getSage(el);
		if (this.sage) {
			html += "<span class=\"de-btn-sage\" title=\"SAGE\"></span>";
		}
		refEl.insertAdjacentHTML("afterend", html + "</span>");
		this.btns = refEl.nextSibling;
		if (Cfg.expandTrunc && this.trunc) {
			this._getFull(this.trunc, true);
		}
		el.addEventListener("mouseover", this, true);
	}
	Post.hiddenNums = [];
	Post.getWrds = function (text) {
		return text.replace(/\s+/g, " ").replace(/[^a-zа-яё ]/ig, "").trim().substring(0, 800).split(" ");
	};
	Post.findSameText = function (oNum, oHid, oWords, date, post) {
		var words = Post.getWrds(post.text),
		    len = words.length,
		    i = oWords.length,
		    olen = i,
		    _olen = i,
		    n = 0;
		if (len < olen * 0.4 || len > olen * 3) {
			return;
		}
		while (i--) {
			if (olen > 6 && oWords[i].length < 3) {
				_olen--;
				continue;
			}
			var j = len;
			while (j--) {
				if (words[j] === oWords[i] || oWords[i].match(/>>\d+/) && words[j].match(/>>\d+/)) {
					n++;
				}
			}
		}
		if (n < _olen * 0.4 || len > _olen * 3) {
			return;
		}
		if (oHid) {
			post.setNote("");
			if (!post.spellHidden) {
				post.setVisib(false);
			}
			if (post.userToggled) {
				delete uVis[post.num];
				post.userToggled = false;
			}
		} else {
			post.setUserVisib(true, date, true);
			post.setNote("similar to >>" + oNum);
		}
		return false;
	};
	Post.sizing = Object.defineProperties({
		handleEvent: function handleEvent() {
			this.wHeight = doc.documentElement.clientHeight;
			this.wWidth = doc.documentElement.clientWidth;
		},

		_enabled: false
	}, {
		dPxRatio: {
			get: function () {
				var val = window.devicePixelRatio || 1;
				Object.defineProperty(this, "dPxRatio", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		wHeight: {
			get: function () {
				var val = doc.documentElement.clientHeight;
				if (!this._enabled) {
					doc.defaultView.addEventListener("resize", this, false);
					this._enabled = true;
				}
				Object.defineProperties(this, {
					wWidth: { writable: true, configurable: true, value: doc.documentElement.clientWidth },
					wHeight: { writable: true, configurable: true, value: val }
				});
				return val;
			},
			configurable: true,
			enumerable: true
		},
		wWidth: {
			get: function () {
				var val = doc.documentElement.clientWidth;
				if (!this._enabled) {
					doc.defaultView.addEventListener("resize", this, false);
					this._enabled = true;
				}
				Object.defineProperties(this, {
					wWidth: { writable: true, configurable: true, value: val },
					wHeight: { writable: true, configurable: true, value: doc.documentElement.clientHeight }
				});
				return val;
			},
			configurable: true,
			enumerable: true
		}
	});
	Post.prototype = Object.defineProperties({
		banned: false,
		deleted: false,
		hasRef: false,
		hidden: false,
		imagesExpanded: false,
		inited: true,
		isPview: false,
		kid: null,
		next: null,
		omitted: false,
		parent: null,
		prev: null,
		spellHidden: false,
		sticked: false,
		userToggled: false,
		viewed: false,
		addFuncs: function addFuncs() {
			updRefMap(this, true);
			embedMediaLinks(this);
			if (Cfg.addImgs) {
				embedImagesLinks(this.el);
			}
			if (isExpImg) {
				this.toggleImages(true);
			}
		},
		handleEvent: function handleEvent(e) {
			var temp,
			    el = e.target,
			    type = e.type,
			    isOutEvent = type === "mouseout";
			if (type === "click") {
				if (e.button !== 0) {
					return;
				}
				switch (el.tagName) {
					case "A":
						if (el.classList.contains("de-video-link")) {
							this.videos.clickLink(el, Cfg.addYouTube);
							$pd(e);
							return;
						}
						temp = el.firstElementChild;
						if (temp && temp.tagName === "IMG") {
							el = temp;
						} else {
							temp = el.parentNode;
							if (temp === this.trunc) {
								this._getFull(temp, false);
								$pd(e);
								e.stopPropagation();
							} else if (Cfg.insertNum && pr.form && temp === this._pref && !/Reply|Ответ/.test(el.textContent)) {
								$pd(e);
								e.stopPropagation();
								if (pr.isQuick || aib.t && pr.isHidden) {
									pr.showQuickReply(this.isPview ? this.getTopParent() : this, this.num, false, true);
								} else if (aib.t) {
									$txtInsert(pr.txta, ">>" + this.num);
								} else {
									window.location = el.href.replace(/#i/, "#");
								}
							}
							return;
						}
										case "IMG":
						if (el.classList.contains("de-video-thumb")) {
							if (Cfg.addYouTube === 3) {
								var vObject = this.videos;
								vObject.currentLink.classList.add("de-current");
								vObject.addPlayer(vObject.playerInfo, el.classList.contains("de-ytube"));
								$pd(e);
							}
						} else if (Cfg.expandImgs !== 0) {
							this._clickImage(el, e);
						}
						return;
					case "OBJECT":
					case "VIDEO":
						if (Cfg.expandImgs !== 0 && !(Cfg.webmControl && e.clientY > el.getBoundingClientRect().top + parseInt(el.style.height, 10) - 30)) {
							this._clickImage(el, e);
						}
						return;
				}
				if (aib.mak && el.classList.contains("expand-large-comment")) {
					this._getFull(el, false);
					$pd(e);
					e.stopPropagation();
				}
				switch (el.className) {
					case "de-btn-expthr":
						this.thr.load(1, false);
						$del(this._menu);
						this._menu = null;
						return;
					case "de-btn-fav":
						this.thr.setFavorState(true);return;
					case "de-btn-fav-sel":
						this.thr.setFavorState(false);return;
					case "de-btn-hide":
					case "de-btn-hide-user":
						if (this.isPview) {
							pByNum[this.num].toggleUserVisib();
							this.btns.firstChild.className = "de-btn-hide-user";
							if (pByNum[this.num].hidden) {
								this.btns.classList.add("de-post-hide");
							} else {
								this.btns.classList.remove("de-post-hide");
							}
						} else {
							this.toggleUserVisib();
						}
						$del(this._menu);
						this._menu = null;
						return;
					case "de-btn-rep":
						pr.showQuickReply(this.isPview ? this.getTopParent() : this, this.num, !this.isPview, false);
						quotetxt = "";
						return;
					case "de-btn-sage":
						addSpell(9, "", false);return;
					case "de-btn-stick":
					case "de-btn-stick-on":
						el.className = this.sticked ? "de-btn-stick" : "de-btn-stick-on";
						this.sticked = !this.sticked;
						return;
				}
				if (el.classList[0] === "de-menu-item") {
					this._clickMenu(el);
				}
				return;
			}
			if (type === "mouseover" && Cfg.expandImgs && !el.classList.contains("de-img-full") && (temp = this.images.getImageByEl(el)) && (temp.isImage || temp.isVideo)) {
				el.title = Cfg.expandImgs === 1 ? Lng.expImgInline[lang] : Lng.expImgFull[lang];
			}
			if (!this._hasEvents) {
				this._hasEvents = true;
				this.el.addEventListener("click", this, true);
				this.el.addEventListener("mouseout", this, true);
			} else if (this.isPview && isOutEvent) {
				this._handleMouseEvents(e.relatedTarget, false);
			}
			switch (el.classList[0]) {
				case "de-btn-expthr":
				case "de-btn-hide":
				case "de-btn-hide-user":
					this._addButtonTitle(el);
								case "de-btn-src":
					if (isOutEvent) {
						this._closeMenu(e.relatedTarget);
					} else {
						this._menuDelay = setTimeout(this._addMenu.bind(this, el), Cfg.linksOver);
					}
					return;
				case "de-btn-rep":
					this._addButtonTitle(el);
					if (!isOutEvent) {
						quotetxt = $txtSelect();
					}
					return;
				case "de-menu":
				case "de-menu-item":
					if (isOutEvent) {
						this._closeMenu(e.relatedTarget);
					} else {
						clearTimeout(this._menuDelay);
					}
					return;
				default:
					if (!Cfg.linksNavig || el.tagName !== "A" || el.lchecked) {
						if (this.isPview && !isOutEvent) {
							this._handleMouseEvents(e.relatedTarget, true);
						}
						return;
					}
					if (!el.textContent.startsWith(">>")) {
						el.lchecked = true;
						return;
					}
				
					el.className = "de-link-pref " + el.className;
								case "de-link-ref":
				case "de-link-pref":
					if (Cfg.linksNavig) {
						if (isOutEvent) {
							clearTimeout(this._linkDelay);
							if (this.kid) {
								this.kid.markToDel();
							} else if (!this.isPview && Pview.top) {
								Pview.top.markToDel();
							}
						} else {
							clearTimeout(Pview.delTO);
							this._linkDelay = setTimeout(this._addPview.bind(this, el), Cfg.linksOver);
						}
						$pd(e);
						e.stopPropagation();
					}
			}
		},
		hideContent: function hideContent(hide) {
			if (hide) {
				this.el.classList.add("de-post-hide");
			} else {
				this.el.classList.remove("de-post-hide");
			}
			if (nav.Chrome) {
				if (hide) {
					this.el.classList.remove("de-post-unhide");
				} else {
					this.el.classList.add("de-post-unhide");
				}
				if (!chromeCssUpd) {
					chromeCssUpd = setTimeout(function () {
						doc.head.insertAdjacentHTML("beforeend", "<style id=\"de-csshide\" type=\"text/css\">\n\t\t\t\t\t\t\t.de-post-hide > " + aib.qHide + " { display: none !important; }\n\t\t\t\t\t\t\t.de-post-unhide > " + aib.qHide + " { display: !important; }\n\t\t\t\t\t\t</style>");
						$del(doc.head.lastChild);
						chromeCssUpd = null;
					}, 200);
				}
			}
		},
		hideRefs: function hideRefs() {
			if (!Cfg.hideRefPsts || !this.hasRef) {
				return;
			}
			this.ref.forEach(function (num) {
				var pst = pByNum[num];
				if (pst && !pst.userToggled) {
					pst.setVisib(true);
					pst.setNote("reference to >>" + this.num);
					pst.hideRefs();
				}
			}, this);
		},
		getAdjacentVisPost: function getAdjacentVisPost(toUp) {
			var post = toUp ? this.prev : this.next;
			while (post) {
				if (post.thr.hidden) {
					post = toUp ? post.thr.op.prev : post.thr.last.next;
				} else if (post.hidden || post.omitted) {
					post = toUp ? post.prev : post.next;
				} else {
					return post;
				}
			}
			return null;
		},
		setNote: function setNote(val) {
			if (this.isOp) {
				this.noteEl.textContent = val ? "(autohide: " + val + ")" : "(" + this.title + ")";
			} else if (!Cfg.delHiddPost) {
				this.noteEl.textContent = val ? "autohide: " + val : "";
			}
		},
		select: function select() {
			if (this.isOp) {
				if (this.hidden) {
					this.thr.el.previousElementSibling.classList.add("de-selected");
				}
				this.thr.el.classList.add("de-selected");
			} else {
				this.el.classList.add("de-selected");
			}
		},
		setUserVisib: function setUserVisib(hide, date, sync) {
			this.setVisib(hide);
			this.btns.firstChild.className = "de-btn-hide-user";
			this.userToggled = true;
			if (hide) {
				this.setNote("");
				this.hideRefs();
			} else {
				this.unhideRefs();
			}
			uVis[this.num] = [+!hide, date];
			if (sync) {
				locStorage["__de-post"] = JSON.stringify({
					brd: aib.b,
					date: date,
					isOp: this.isOp,
					num: this.num,
					hide: hide,
					title: this.isOp ? this.title : ""
				});
				locStorage.removeItem("__de-post");
			}
		},
		setVisib: function setVisib(hide) {
			var _this = this;

			if (this.hidden === hide) {
				return;
			}
			if (this.isOp) {
				this.hidden = this.thr.hidden = hide;
				var el = $id("de-thr-hid-" + this.num),
				    tEl = this.thr.el;
				tEl.style.display = hide ? "none" : "";
				if (el) {
					el.style.display = hide ? "" : "none";
					return;
				}
				tEl.insertAdjacentHTML("beforebegin", "<div class=\"" + aib.cReply + " de-thr-hid\" id=\"de-thr-hid-" + this.num + "\">" + Lng.hiddenThrd[lang] + " <a href=\"#\">№" + this.num + "</a> <span class=\"de-thread-note\"></span></div>");
				el = $t("a", tEl.previousSibling);
				el.onclick = el.onmouseover = el.onmouseout = function (e) {
					switch (e.type) {
						case "click":
							_this.toggleUserVisib();
							$pd(e);
							return;
						case "mouseover":
							_this.thr.el.style.display = "";return;
						default:
						
							if (_this.hidden) {
								_this.thr.el.style.display = "none";
							}
					}
				};
				return;
			}
			if (Cfg.delHiddPost) {
				if (hide) {
					this.wrap.classList.add("de-hidden");
				} else if (this.hidden) {
					this.wrap.classList.remove("de-hidden");
				}
			} else {
				if (!hide) {
					this.setNote("");
				}
				this._pref.onmouseover = this._pref.onmouseout = !hide ? null : function (e) {
					return _this.hideContent(e.type === "mouseout");
				};
			}
			this.hidden = hide;
			this.hideContent(hide);
			if (Cfg.strikeHidd) {
				setTimeout(this._strikePostNum.bind(this, hide), 50);
			}
		},
		spellHide: function spellHide(note) {
			this.spellHidden = true;
			if (!this.userToggled) {
				if (aib.t && !this.deleted) {
					sVis[this.count] = 0;
				}
				if (!this.hidden) {
					this.hideRefs();
				}
				this.setVisib(true);
				this.setNote(note);
			}
		},
		spellUnhide: function spellUnhide() {
			this.spellHidden = false;
			if (!this.userToggled) {
				if (aib.t && !this.deleted) {
					sVis[this.count] = 1;
				}
				this.setVisib(false);
				this.unhideRefs();
			}
		},
		toggleImages: function toggleImages(expand) {
			var _iteratorNormalCompletion = true;
			var _didIteratorError = false;
			var _iteratorError = undefined;

			try {
				for (var _iterator = this.images.data[Symbol.iterator](), _step; !(_iteratorNormalCompletion = (_step = _iterator.next()).done); _iteratorNormalCompletion = true) {
					var image = _step.value;

					if (image.isImage && image.expanded ^ expand) {
						if (expand) {
							image.expand(true, null);
						} else {
							image.collapse(null);
						}
					}
				}
			} catch (err) {
				_didIteratorError = true;
				_iteratorError = err;
			} finally {
				try {
					if (!_iteratorNormalCompletion && _iterator["return"]) {
						_iterator["return"]();
					}
				} finally {
					if (_didIteratorError) {
						throw _iteratorError;
					}
				}
			}

			this.imagesExpanded = expand;
		},
		toggleUserVisib: function toggleUserVisib() {
			var isOp = this.isOp,
			    hide = !this.hidden;
			this.setUserVisib(hide, Date.now(), true);
			if (isOp) {
				if (hide) {
					hThr[aib.b][this.num] = this.title;
				} else {
					delete hThr[aib.b][this.num];
				}
				saveHiddenThreads(false);
			}
			saveUserPosts();
		},
		unhideRefs: function unhideRefs() {
			if (!Cfg.hideRefPsts || !this.hasRef) {
				return;
			}
			this.ref.forEach(function (num) {
				var pst = pByNum[num];
				if (pst && pst.hidden && !pst.userToggled && !pst.spellHidden) {
					pst.setVisib(false);
					pst.unhideRefs();
				}
			});
		},
		unselect: function unselect() {
			if (this.isOp) {
				var el = $id("de-thr-hid-" + this.num);
				if (el) {
					el.classList.remove("de-selected");
				}
				this.thr.el.classList.remove("de-selected");
			} else {
				this.el.classList.remove("de-selected");
			}
		},
		updateMsg: function updateMsg(newMsg) {
			var origMsg = aib.dobr ? this.msg.firstElementChild : this.msg,
			    videoExt = $c("de-video-ext", origMsg),
			    videoLinks = $Q(":not(.de-video-ext) > .de-video-link", origMsg);
			origMsg.parentNode.replaceChild(newMsg, origMsg);
			Object.defineProperties(this, {
				msg: { configurable: true, value: newMsg },
				trunc: { configurable: true, value: null }
			});
			PostContent.remove(this);
			this.videos.updatePost(videoLinks, $Q("a[href*=\"youtu\"], a[href*=\"vimeo.com\"]", newMsg), false);
			if (videoExt) {
				newMsg.appendChild(videoExt);
			}
			this.addFuncs();
			var sRunner = new SpellsRunner();
			sRunner.run(this);
			sRunner.end();
			closeAlert($id("de-alert-load-fullmsg"));
		},

		_hasEvents: false,
		_linkDelay: 0,
		_menu: null,
		_menuDelay: 0,
		_selRange: null,
		_selText: "",
		_addButtonTitle: function _addButtonTitle(el) {
			if (el.hasTitle) {
				return;
			}
			el.hasTitle = true;
			switch (el.className) {
				case "de-btn-hide":
				case "de-btn-hide-user":
					el.title = Lng.togglePost[lang];return;
				case "de-btn-expthr":
					el.title = Lng.expandThrd[lang];return;
				case "de-btn-rep":
					el.title = Lng.replyToPost[lang];
			}
		},
		_addMenu: function _addMenu(el) {
			var html,
			    isLeft = false,
			    className = "de-menu " + aib.cReply;
			switch (el.getAttribute("de-menu")) {
				case "hide":
					if (!Cfg.menuHiddBtn) {
						return;
					}
					html = this._addMenuHide();
					break;
				case "expand":
					html = "<span class=\"de-menu-item\" info=\"thr-exp\">" + Lng.selExpandThr[lang].join("</span><span class=\"de-menu-item\" info=\"thr-exp\">") + "</span>";
					break;
				case "imgsrc":
					isLeft = true;
					className += " de-imgmenu";
					html = this._addMenuImgSrc(el);
			}
			var cr = el.getBoundingClientRect(),
			    xOffset = window.pageXOffset;
			doc.body.insertAdjacentHTML("beforeend", "<div class=\"" + className + "\" style=\"position: absolute; " + (isLeft ? "left: " + (cr.left + xOffset) : "right: " + (doc.documentElement.clientWidth - cr.right - xOffset)) + "px; top: " + (window.pageYOffset + cr.bottom) + "px;\">" + html + "</div>");
			if (this._menu) {
				clearTimeout(this._menuDelay);
				$del(this._menu);
			}
			this._menu = doc.body.lastChild;
			this._menu.addEventListener("click", this, false);
			this._menu.addEventListener("mouseover", this, false);
			this._menu.addEventListener("mouseout", this, false);
		},
		_addMenuHide: function _addMenuHide() {
			var str = "",
			    sel = nav.Presto ? doc.getSelection() : window.getSelection(),
			    ssel = sel.toString(),
			    addItem = function addItem(name) {
				str += "<span info=\"spell-" + name + "\" class=\"de-menu-item\">" + Lng.selHiderMenu[name][lang] + "</span>";
			};
			if (ssel) {
				this._selText = ssel;
				this._selRange = sel.getRangeAt(0);
				addItem("sel");
			}
			if (this.posterName) {
				addItem("name");
			}
			if (this.posterTrip) {
				addItem("trip");
			}
			if (this.images.hasAttachments) {
				addItem("img");
				addItem("ihash");
			} else {
				addItem("noimg");
			}
			if (this.text) {
				addItem("text");
			} else {
				addItem("notext");
			}
			return str;
		},
		_addMenuImgSrc: function _addMenuImgSrc(el) {
			var p = el.nextSibling.href + "\" target=\"_blank\">" + Lng.search[lang],
			    c = doc.body.getAttribute("de-image-search"),
			    str = "";
			if (c) {
				c = c.split(";");
				c.forEach(function (el) {
					var info = el.split(",");
					str += "<a class=\"de-src" + info[0] + (!info[1] ? "\" onclick=\"de_isearch(event, '" + info[0] + "')\" de-url=\"" : "\" href=\"" + info[1]) + p + info[0] + "</a>";
				});
			}
			return "<a class=\"de-menu-item de-imgmenu de-src-google\" href=\"http://google.com/searchbyimage?image_url=" + p + "Google</a>" + "<a class=\"de-menu-item de-imgmenu de-src-yandex\" href=\"http://yandex.ru/images/search?rpt=imageview&img_url=" + p + "Yandex</a>" + "<a class=\"de-menu-item de-imgmenu de-src-tineye\" href=\"http://tineye.com/search/?url=" + p + "TinEye</a>" + "<a class=\"de-menu-item de-imgmenu de-src-saucenao\" href=\"http://saucenao.com/search.php?url=" + p + "SauceNAO</a>" + "<a class=\"de-menu-item de-imgmenu de-src-iqdb\" href=\"http://iqdb.org/?url=" + p + "IQDB</a>" + str;
		},
		_addPview: function _addPview(link) {
			var tNum = (link.pathname.match(/.+?\/[^\d]*(\d+)/) || [, aib.getPostEl(link).post.tNum])[1],
			    pNum = (link.textContent.trim().match(/\d+$/) || [tNum])[0],
			    pv = this.isPview ? this.kid : Pview.top;
			if (pv && pv.num === pNum) {
				Pview.del(pv.kid);
				setPviewPosition(link, pv.el, Cfg.animation);
				if (pv.parent.num !== this.num) {
					$each($C("de-link-pview", pv.el), function (el) {
						el.classList.remove("de-link-pview");
					});
					pv._markLink(this.num);
				}
				this.kid = pv;
				pv.parent = this;
			} else if (!Cfg.noNavigHidd || !pByNum[pNum] || !pByNum[pNum].hidden) {
				this.kid = new Pview(this, link, tNum, pNum);
			}
		},
		_clickImage: function _clickImage(el, e) {
			var data;
			if (el.classList.contains("de-img-full")) {
				if (!this.images.getImageByEl(el.previousSibling.firstElementChild).collapse(e)) {
					return;
				}
			} else if ((data = this.images.getImageByEl(el)) && (data.isImage || data.isVideo)) {
				data.expand(Cfg.expandImgs === 1 ^ e.ctrlKey, e);
			} else {
				return;
			}
			$pd(e);
			e.stopPropagation();
		},
		_clickMenu: function _clickMenu(el) {
			$del(this._menu);
			this._menu = null;
			switch (el.getAttribute("info")) {
				case "spell-sel":
					var start = this._selRange.startContainer,
					    end = this._selRange.endContainer;
					if (start.nodeType === 3) {
						start = start.parentNode;
					}
					if (end.nodeType === 3) {
						end = end.parentNode;
					}
					var inMsgSel = aib.qMsg + ", " + aib.qMsg + " *";
					if (nav.matchesSelector(start, inMsgSel) && nav.matchesSelector(end, inMsgSel) || nav.matchesSelector(start, "." + aib.cSubj) && nav.matchesSelector(end, "." + aib.cSubj)) {
						if (this._selText.includes("\n")) {
							addSpell(1, "/" + regQuote(this._selText).replace(/\r?\n/g, "\\n") + "/", false);
						} else {
							addSpell(0, this._selText.replace(/\)/g, "\\)").toLowerCase(), false);
						}
					} else {
						dummy.innerHTML = "";
						dummy.appendChild(this._selRange.cloneContents());
						addSpell(2, "/" + regQuote(dummy.innerHTML.replace(/^<[^>]+>|<[^>]+>$/g, "")) + "/", false);
					}
					return;
				case "spell-name":
					addSpell(6, this.posterName.replace(/\)/g, "\\)"), false);return;
				case "spell-trip":
					addSpell(7, this.posterTrip.replace(/\)/g, "\\)"), false);return;
				case "spell-img":
					var img = this.images.firstAttach,
					    w = img.weight,
					    wi = img.width,
					    h = img.height;
					addSpell(8, [0, [w, w], [wi, wi, h, h]], false);
					return;
				case "spell-ihash":
					spawn(ImagesHashStorage.getHash, this.images.firstAttach).then(function (hash) {
						if (hash !== -1) {
							addSpell(4, hash, false);
						}
					});
					return;
				case "spell-noimg":
					addSpell(264, "", true);return;
				case "spell-text":
					var num = this.num,
					    hidden = this.hidden,
					    wrds = Post.getWrds(this.text),
					    time = Date.now();
					for (var post = dForm.firstThr.op; post; post = post.next) {
						Post.findSameText(num, hidden, wrds, time, post);
					}
					saveUserPosts();
					return;
				case "spell-notext":
					addSpell(267, "", true);return;
				case "thr-exp":
					this.thr.load(parseInt(el.textContent, 10), false);
			}
		},
		_closeMenu: function _closeMenu(rt) {
			var _this = this;

			clearTimeout(this._menuDelay);
			if (this._menu && (!rt || rt.className !== "de-menu-item")) {
				this._menuDelay = setTimeout(function () {
					$del(_this._menu);
					_this._menu = null;
				}, 75);
			}
		},
		_getFull: function _getFull(node, isInit) {
			var _this = this;

			if (aib.dobr) {
				$del(node.nextSibling);
				$del(node.previousSibling);
				$del(node);
				if (isInit) {
					this.msg.replaceChild($q(".alternate > div", this.el), this.msg.firstElementChild);
				} else {
					this.updateMsg($q(".alternate > div", this.el));
				}
				return;
			}
			if (aib.mak) {
				$del(node.previousSibling);
				node.previousSibling.style.display = "";
				$del(node);
				return;
			}
			if (!isInit) {
				$alert(Lng.loading[lang], "load-fullmsg", true);
			}
			ajaxLoad(aib.getThrdUrl(aib.b, this.tNum)).then(function (form) {
				if (_this.isOp) {
					_this.updateMsg(replacePost($q(aib.qMsg, form)));
					$del(node);
					return;
				}
				var els = $Q(aib.qRPost, form);
				for (var i = 0, len = els.length; i < len; i++) {
					if (_this.num === aib.getPNum(els[i])) {
						_this.updateMsg(replacePost($q(aib.qMsg, els[i])));
						$del(node);
						return;
					}
				}
			}, emptyFn);
		},
		_markLink: function _markLink(pNum) {
			$each($Q("a[href*=\"" + pNum + "\"]", this.el), (function (num, el) {
				if (el.textContent === ">>" + num) {
					el.classList.add("de-link-pview");
				}
			}).bind(null, pNum));
		},
		_strikePostNum: function _strikePostNum(isHide) {
			var num = this.num;
			if (isHide) {
				Post.hiddenNums.push(+num);
			} else {
				var idx = Post.hiddenNums.indexOf(+num);
				if (idx !== -1) {
					Post.hiddenNums.splice(idx, 1);
				}
			}
			$each($Q("a[href*=\"#" + num + "\"]", dForm.el), isHide ? function (el) {
				el.classList.add("de-link-hid");
				if (Cfg.removeHidd && el.classList.contains("de-link-ref")) {
					var refmap = el.parentNode;
					if (!$q(".de-link-ref:not(.de-link-hid)", refmap)) {
						refmap.style.display = "none";
					}
				}
			} : function (el) {
				el.classList.remove("de-link-hid");
				if (Cfg.removeHidd && el.classList.contains("de-link-ref")) {
					var refmap = el.parentNode;
					if ($q(".de-link-ref:not(.de-link-hid)", refmap)) {
						refmap.style.display = "";
					}
				}
			});
		}
	}, {
		html: {
			get: function () {
				return PostContent.get(this).html;
			},
			configurable: true,
			enumerable: true
		},
		images: {
			get: function () {
				var val = new PostImages(this);
				Object.defineProperty(this, "images", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		mp3Obj: {
			get: function () {
				var val = $new("div", { "class": "de-mp3" }, null);
				$before(this.msg, val);
				Object.defineProperty(this, "mp3Obj", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		msg: {
			get: function () {
				var val = $q(aib.qMsg, this.el);
				Object.defineProperty(this, "msg", { configurable: true, value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		nextInThread: {
			get: function () {
				var post = this.next;
				return !post || post.count === 0 ? null : post;
			},
			configurable: true,
			enumerable: true
		},
		nextNotDeleted: {
			get: function () {
				var post = this.nextInThread;
				while (post && post.deleted) {
					post = post.nextInThread;
				}
				return post;
			},
			configurable: true,
			enumerable: true
		},
		noteEl: {
			get: function () {
				var val;
				if (this.isOp) {
					val = this.thr.el.previousElementSibling.lastChild;
				} else {
					this.btns.insertAdjacentHTML("beforeend", "<span class=\"de-post-note\"></span>");
					val = this.btns.lastChild;
				}
				Object.defineProperty(this, "noteEl", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		posterName: {
			get: function () {
				return PostContent.get(this).posterName;
			},
			configurable: true,
			enumerable: true
		},
		posterTrip: {
			get: function () {
				return PostContent.get(this).posterTrip;
			},
			configurable: true,
			enumerable: true
		},
		subj: {
			get: function () {
				return PostContent.get(this).subj;
			},
			configurable: true,
			enumerable: true
		},
		text: {
			get: function () {
				return PostContent.get(this).text;
			},
			configurable: true,
			enumerable: true
		},
		title: {
			get: function () {
				return PostContent.get(this).title;
			},
			configurable: true,
			enumerable: true
		},
		tNum: {
			get: function () {
				return this.thr.num;
			},
			configurable: true,
			enumerable: true
		},
		topCoord: {
			get: function () {
				return (this.isOp && this.hidden ? this.thr.el.previousElementSibling : this.el).getBoundingClientRect().top;
			},
			configurable: true,
			enumerable: true
		},
		trunc: {
			get: function () {
				var el = aib.qTrunc && $q(aib.qTrunc, this.el),
				    val = null;
				if (el && /long|full comment|gekürzt|слишком|длинн|мног|полн/i.test(el.textContent)) {
					val = el;
				}
				Object.defineProperty(this, "trunc", { configurable: true, value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		videos: {
			get: function () {
				var val = Cfg.addYouTube ? new Videos(this) : null;
				Object.defineProperty(this, "videos", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		wrap: {
			get: function () {
				var val = aib.getWrap(this.el, this.isOp);
				Object.defineProperty(this, "wrap", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		}
	});

	function PostContent(post) {
		this.el = post.el;
		this.post = post;
	}
	PostContent.data = {};
	PostContent.purgeTO = null;
	PostContent.get = function (post) {
		var pNum = post.num,
		    rv = PostContent.data[pNum];
		if (!rv) {
			PostContent.data[pNum] = rv = new PostContent(post);
		}
		if (PostContent.purgeTO === null) {
			PostContent.purgeTO = setTimeout(PostContent.purge, 60000);
		}
		return rv;
	};
	PostContent.purge = function () {
		if (PostContent.purgeTO !== null) {
			clearTimeout(PostContent.purgeTO);
			PostContent.purgeTO = null;
		}
		PostContent.data = {};
	};
	PostContent.remove = function (post) {
		PostContent.data[post.num] = null;
	};
	PostContent.prototype = Object.defineProperties({}, {
		html: {
			get: function () {
				var val = this.el.innerHTML;
				Object.defineProperty(this, "html", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		posterName: {
			get: function () {
				var pName = $q(aib.qName, this.el),
				    val = pName ? pName.textContent.trim().replace(/\s/g, " ") : "";
				Object.defineProperty(this, "posterName", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		posterTrip: {
			get: function () {
				var pTrip = $c(aib.cTrip, this.el),
				    val = pTrip ? pTrip.textContent : "";
				Object.defineProperty(this, "posterTrip", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		subj: {
			get: function () {
				var subj = $c(aib.cSubj, this.el),
				    val = subj ? subj.textContent : "";
				Object.defineProperty(this, "subj", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		text: {
			get: function () {
				var val = this.post.msg.innerHTML.replace(/<\/?(?:br|p|li)[^>]*?>/gi, "\n").replace(/<[^>]+?>/g, "").replace(/&gt;/g, ">").replace(/&lt;/g, "<").replace(/&nbsp;/g, " ");
				Object.defineProperty(this, "text", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		},
		title: {
			get: function () {
				var val = this.subj || this.text.substring(0, 70).replace(/\s+/g, " ");
				Object.defineProperty(this, "title", { value: val });
				return val;
			},
			configurable: true,
			enumerable: true
		}
	});

	function PostImages(post) {
		var els = $Q(aib.qThumbImages, post.el),
		    filesMap = new WeakMap(),
		    data = [],
		    hasAttachments = false,
		    idx = 0;
		for (var i = 0, len = els.length; i < len; ++i, ++idx) {
			var el = els[i],
			    obj = new Attachment(post, el, idx);
			filesMap.set(el, obj);
			data.push(obj);
			hasAttachments = true;
		}
		if (Cfg.addImgs) {
			els = aProto.slice.call($C("de-img-pre", post.el));
			for (var i = 0, len = els.length; i < len; ++i, ++idx) {
				var el = els[i],
				    obj = new EmbeddedImage(post, el, idx);
				filesMap.set(el, obj);
				data.push(obj);
			}
		}
		this.data = data;
		this.length = data.length;
		this.hasAttachments = hasAttachments;
		this._map = filesMap;
	}
	PostImages.prototype = Object.defineProperties({
		getImageByEl: function getImageByEl(el) {
			return this._map.get(el);
		}
	}, {
		first: {
			get: function () {
				return this.data[0];
			},
			configurable: true,
			enumerable: true
		},
		firstAttach: {
			get: function () {
				for (var i = 0; i < this.length; ++i) {
					var obj = this.data[i];
					if (obj instanceof Attachment) {
						return obj;
					}
				}
				return null;
			},
			configurable: true,
			enumerable: true
		},
		last: {
			get: function () {
				return this.data[this.length - 1];
			},
			configurable: true,
			enumerable: true
		}
	});




	function Pview(parent, link, tNum, pNum) {
		var _this = this;

		this.parent = parent;
		this._link = link;
		this.num = pNum;
		this.thr = parent.thr;
		Object.defineProperty(this, "tNum", { value: tNum });
		var post = pByNum[pNum];
		if (post && (!post.isOp || !parent.isPview || !parent._loaded)) {
			this._showPost(post);
			return;
		}
		var b = link.pathname.match(/^\/?(.+\/)/)[1].replace(aib.res, "").replace(/\/$/, "");
		post = this._cache && this._cache[b + tNum] && this._cache[b + tNum].getPost(pNum);
		if (post) {
			this._loaded = true;
			this._showPost(post);
		} else {
			this._showText("<span class=\"de-wait\">" + Lng.loading[lang] + "</span>");
			ajaxLoad(aib.getThrdUrl(b, tNum)).then(function (form) {
				return _this._onload(b, form);
			}, function (e) {
				return _this._onerror(e);
			});
		}
	}
	Pview.clearCache = function () {
		Pview.prototype._cache = {};
	};
	Pview.del = function (pv) {
		if (!pv) {
			return;
		}
		pv.parent.kid = null;
		pv._link.classList.remove("de-link-parent");
		if (!pv.parent.isPview) {
			Pview.top = null;
		}
		var vPost = Attachment.viewer && Attachment.viewer.data.post;
		do {
			clearTimeout(pv._readDelay);
			if (vPost === pv) {
				Attachment.viewer.close(null);
				Attachment.viewer = vPost = null;
			}
			var el = pv.el;
			if (Cfg.animation) {
				nav.animEvent(el, $del);
				el.classList.add("de-pview-anim");
				el.style[nav.animName] = "de-post-close-" + (el.aTop ? "t" : "b") + (el.aLeft ? "l" : "r");
			} else {
				$del(el);
			}
		} while (pv = pv.kid);
	};
	Pview.mouseEnter = function (post) {
		if (post.kid) {
			post.kid.markToDel();
		} else {
			clearTimeout(Pview.delTO);
		}
	};
	Pview.delTO = 0;
	Pview.top = null;
	Pview.prototype = Object.create(Post.prototype, {
		isPview: { value: true },
		getTopParent: { value: function value() {
				var post = this.parent;
				while (post.isPview) {
					post = post.parent;
				}
				return post;
			} },
		markToDel: { value: function value() {
				clearTimeout(Pview.delTO);
				var lastSticked,
				    el = this;
				do {
					if (el.sticked) {
						lastSticked = el;
					}
				} while (el = el.kid);
				if (!lastSticked || lastSticked.kid) {
					Pview.delTO = setTimeout(Pview.del, Cfg.linksOut, lastSticked ? lastSticked.kid : this);
				}
			} },

		_loaded: { value: false, writable: true },
		_cache: { value: {}, writable: true },
		_readDelay: { value: 0, writable: true },
		_handleMouseEvents: { value: function value(el, isOverEvent) {
				if (!el || el !== this.el && !this.el.contains(el)) {
					if (isOverEvent) {
						Pview.mouseEnter(this);
					} else if (Pview.top && (!this._menu || this._menu !== el && !this._menu.contains(el))) {
						Pview.top.markToDel();
					}
				}
			} },
		_onerror: { value: function value(e) {
				Pview.del(this);
				this._showText(e instanceof AjaxError && e.code === 404 ? Lng.postNotFound[lang] : getErrorMessage(e));
			} },
		_onload: { value: function value(b, form) {
				var parent = this.parent,
				    parentNum = parent.num,
				    cache = this._cache[b + this.tNum] = new PviewsCache(form, b, this.tNum),
				    post = cache.getPost(this.num);
				if (post && (aib.b !== b || !post.hasRef || post.ref.indexOf(parentNum) === -1)) {
					var rm;
					if (post.hasRef) {
						rm = $c("de-refmap", post.el);
					} else {
						post.msg.insertAdjacentHTML("afterend", "<div class=\"de-refmap\"></div>");
						rm = post.msg.nextSibling;
					}
					rm.insertAdjacentHTML("afterbegin", "<a class=\"de-link-ref\" href=\"" + aib.getThrdUrl(b, parent.tNum) + aib.anchor + parentNum + "\">&gt;&gt;" + (aib.b === b ? "" : "/" + aib.b + "/") + parentNum + "</a><span class=\"de-refcomma\">, </span>");
				}
				if (parent.kid === this) {
					Pview.del(this);
					if (post) {
						this._loaded = true;
						this._showPost(post);
					} else {
						this._showText(Lng.postNotFound[lang]);
					}
				}
			} },
		_showPost: { value: function value(post) {
				var el = this.el = post.el.cloneNode(true),
				    pText = "<span class=\"de-btn-rep\" title=\"" + Lng.replyToPost[lang] + "\"></span>" + (post.sage ? "<span class=\"de-btn-sage\" title=\"SAGE\"></span>" : "") + "<span class=\"de-btn-stick\" title=\"" + Lng.attachPview[lang] + "\"></span>" + (post.deleted ? "" : "<span style=\"margin-right: 4px; vertical-align: 1px; color: #4f7942; " + "font: bold 11px tahoma; cursor: default;\">" + (post.isOp ? "OP" : post.count + 1) + "</span>");
				el.post = this;
				el.className = aib.cReply + " de-pview" + (post.viewed ? " de-viewed" : "");
				el.style.display = "";
				if (Cfg.linksNavig === 2) {
					this._markLink(this.parent.num);
				}
				this._pref = $q(aib.qRef, el);
				this._link.classList.add("de-link-parent");
				if (post.inited) {
					var node = $c("de-post-btns", el);
					this.btns = node;
					this.isOp = post.isOp;
					node.classList.remove("de-post-counter");
					if (post.hidden) {
						node.classList.add("de-post-hide");
					}
					node.innerHTML = "<span class=\"de-btn-hide" + (post.userToggled ? "-user" : "") + "\" de-menu=\"hide\" title=\"" + Lng.togglePost[lang] + "\"></span>" + pText;
					$each($Q((!aib.t && post.isOp ? aib.qOmitted + ", " : "") + ".de-img-full, .de-after-fimg", el), $del);
					$each($Q(aib.qThumbImages, el), function (el) {
						el.parentNode.style.display = "";
					});
					node = $c("de-link-parent", el);
					if (node) {
						node.classList.remove("de-link-parent");
					}
					if (Cfg.addYouTube && post.videos.hasLinks) {
						if (post.videos.playerInfo !== null) {
							Object.defineProperty(this, "videos", {
								value: new Videos(this, $c("de-video-obj", el), post.videos.playerInfo)
							});
						}
						this.videos.updatePost($C("de-video-link", post.el), $C("de-video-link", el), true);
					}
					if (Cfg.addImgs) {
						$each($C("de-img-pre", el), function (el) {
							el.style.display = "";
						});
					}
					if (Cfg.markViewed) {
						this._readDelay = setTimeout(function (pst) {
							if (!pst.viewed) {
								pst.el.classList.add("de-viewed");
								pst.viewed = true;
							}
							var arr = (sesStorage["de-viewed"] || "").split(",");
							arr.push(pst.num);
							sesStorage["de-viewed"] = arr;
						}, post.text.length > 100 ? 2000 : 500, post);
					}
				} else {
					this._pref.insertAdjacentHTML("afterend", "<span class=\"de-post-btns\">" + pText + "</span");
					embedMediaLinks(this);
					if (Cfg.addYouTube) {
						new VideosParser().parse(this).end();
					}
					if (Cfg.addImgs) {
						embedImagesLinks(el);
					}
					processImageNames(el);
				}
				el.addEventListener("click", this, true);
				this._showPview(el);
			} },
		_showPview: { value: function value(el, id) {
				if (this.parent.isPview) {
					Pview.del(this.parent.kid);
				} else {
					Pview.del(Pview.top);
					Pview.top = this;
				}
				this.parent.kid = this;
				el.addEventListener("mouseover", this, true);
				el.addEventListener("mouseout", this, true);
				dForm.el.appendChild(el);
				setPviewPosition(this._link, el, false);
				if (Cfg.animation) {
					nav.animEvent(el, function (node) {
						node.classList.remove("de-pview-anim");
						node.style[nav.animName] = "";
					});
					el.classList.add("de-pview-anim");
					el.style[nav.animName] = "de-post-open-" + (el.aTop ? "t" : "b") + (el.aLeft ? "l" : "r");
				}
			} },
		_showText: { value: function value(txt) {
				this._showPview(this.el = $add("<div class=\"" + aib.cReply + " de-pview-info de-pview\">" + txt + "</div>"));
			} } });

	function PviewsCache(form, b, tNum) {
		var pBn = {},
		    pProto = Post.prototype,
		    thr = $q(aib.qThread, form) || form,
		    posts = $Q(aib.qRPost, thr);
		for (var i = 0, len = posts.length; i < len; ++i) {
			var post = posts[i];
			pBn[aib.getPNum(post)] = Object.create(pProto, {
				count: { value: i + 1 },
				el: { value: post, writable: true },
				inited: { value: false },
				pvInited: { value: false, writable: true },
				ref: { value: [], writable: true }
			});
		}
		pBn[tNum] = this._opObj = Object.create(pProto, {
			inited: { value: false },
			isOp: { value: true },
			msg: { value: $q(aib.qMsg, thr), writable: true },
			ref: { value: [], writable: true }
		});
		this._b = b;
		this._thr = thr;
		this._tNum = tNum;
		this._tUrl = aib.getThrdUrl(b, tNum);
		this._posts = pBn;
		if (Cfg.linksNavig === 2) {
			genRefMap(pBn, this._tUrl);
		}
	}
	PviewsCache.prototype = Object.defineProperties({
		getPost: function getPost(num) {
			if (num === this._tNum) {
				return this._op;
			}
			var pst = this._posts[num];
			if (pst && !pst.pvInited) {
				pst.el = replacePost(pst.el);
				delete pst.msg;
				if (pst.hasRef) {
					addRefMap(pst, this._tUrl);
				}
				pst.pvInited = true;
			}
			return pst;
		} }, {
		_op: {
			get: function () {
				var oOp,
				    op = this._opObj;
				op.el = replacePost(aib.getOp(this._thr));
				op.msg = $q(aib.qMsg, op.el);
				if (this._b === aib.b && (oOp = pByNum[this._tNum])) {
					var i,
					    j,
					    len,
					    rRef = [],
					    oRef = op.ref,
					    nRef = oOp.ref;
					for (i = j = 0, len = nRef.length; j < len; ++j) {
						var num = nRef[j];
						if (oRef[i] === num) {
							i++;
						} else if (oRef.indexOf(num) !== -1) {
							continue;
						}
						rRef.push(num);
					}
					for (len = oRef.length; i < len; i++) {
						rRef.push(oRef[i]);
					}
					op.ref = rRef;
					if (rRef.length) {
						op.hasRef = true;
						addRefMap(op, this._tUrl);
					}
				} else if (op.hasRef) {
					addRefMap(op, this._tUrl);
				}
				Object.defineProperty(this, "_op", { value: op });
				return op;
			},
			configurable: true,
			enumerable: true
		}
	});

	function PviewMoved() {
		if (this.style[nav.animName]) {
			this.classList.remove("de-pview-anim");
			this.style.cssText = this.newPos;
			this.newPos = false;
			$each($C("de-css-move", doc.head), $del);
			this.removeEventListener(nav.animEnd, PviewMoved, false);
		}
	}

	function setPviewPosition(link, pView, isAnim) {
		if (pView.link === link) {
			return;
		}
		var oldCSS,
		    cr = link.getBoundingClientRect(),
		    offX = cr.left + window.pageXOffset + link.offsetWidth / 2,
		    offY = cr.top + window.pageYOffset,
		    bWidth = doc.documentElement.clientWidth,
		    isLeft = offX < bWidth / 2,
		    tmp = isLeft ? offX : offX - Math.min(parseInt(pView.offsetWidth, 10), offX - 10),
		    lmw = "max-width:" + (bWidth - tmp - 10) + "px; left:" + tmp + "px;";
		if (isAnim) {
			oldCSS = pView.style.cssText;
			pView.style.cssText = "opacity: 0; " + lmw;
		} else {
			pView.style.cssText = lmw;
		}
		var top = pView.offsetHeight,
		    isTop = top + cr.top + link.offsetHeight < doc.documentElement.clientHeight || cr.top - top < 5;
		top = (isTop ? offY + link.offsetHeight : offY - top) + "px";
		pView.link = link;
		pView.aLeft = isLeft;
		pView.aTop = isTop;
		if (!isAnim) {
			pView.style.top = top;
			return;
		}
		var uId = "de-movecss-" + Math.round(Math.random() * 1000);
		$css("@" + nav.cssFix + "keyframes " + uId + " {to { " + lmw + " top:" + top + "; }}").className = "de-css-move";
		if (pView.newPos) {
			pView.style.cssText = pView.newPos;
			pView.removeEventListener(nav.animEnd, PviewMoved, false);
		} else {
			pView.style.cssText = oldCSS;
		}
		pView.newPos = lmw + " top:" + top + ";";
		pView.addEventListener(nav.animEnd, PviewMoved, false);
		pView.classList.add("de-pview-anim");
		pView.style[nav.animName] = uId;
	}

	function addRefMap(post, tUrl) {
		var bStr = "<a " + aib.rLinkClick + " href=\"" + tUrl + aib.anchor,
		    strNums = Cfg.strikeHidd && Post.hiddenNums.length ? Post.hiddenNums : null,
		    html = ["<div class=\"de-refmap\">"],
		    el = post.ref;
		for (var i = 0, len = el.length; i < len; ++i) {
			var num = el[i];
			html.push(bStr, num, "\" class=\"de-link-ref ", strNums && strNums.indexOf(+num) !== -1 ? "de-link-hid" : "", "\">&gt;&gt;", num, "</a><span class=\"de-refcomma\">, </span>");
		}
		html.push("</div>");
		if (aib.dobr) {
			el = post.msg.nextElementSibling;
			if (el) {
				el.insertAdjacentHTML("beforeend", html.join(""));
			}
		} else {
			post.msg.insertAdjacentHTML("afterend", html.join(""));
		}
	}

	function genRefMap(posts, thrURL) {
		var opNums = dForm.tNums;
		for (var pNum in posts) {
			var links = $T("a", posts[pNum].msg);
			for (var i = 0, len = links.length; i < len; ++i) {
				var lNum,
				    tc = links[i].textContent;
				if (tc[0] === ">" && tc[1] === ">" && (lNum = +tc.substr(2)) && lNum in posts) {
					var post = posts[lNum],
					    ref = post.ref;
					if (ref.indexOf(pNum) === -1) {
						ref.push(pNum);
						post.hasRef = true;
					}
					if (opNums.indexOf(lNum) !== -1) {
						links[i].classList.add("de-ref-op");
					}
					if (thrURL) {
						var url = links[i].getAttribute("href");
						if (url[0] === "#") {
							links[i].setAttribute("href", thrURL + url);
						}
					}
				}
			}
		}
	}

	function updRefMap(post, add) {
		var pNum = post.num,
		    links = $T("a", post.msg),
		    strNums = add && Cfg.strikeHidd && Post.hiddenNums.length ? Post.hiddenNums : null,
		    opNums = add && dForm.tNums,
		    isThr = aib.t;
		for (var i = 0, len = links.length; i < len; ++i) {
			var lNum,
			    link = links[i],
			    tc = link.textContent;
			if (tc[0] === ">" && tc[1] === ">" && (lNum = +tc.substr(2)) && lNum in pByNum) {
				var lPost = pByNum[lNum];
				if (!isThr) {
					link.href = "#" + (aib.fch ? "p" : "") + lNum;
				}
				if (add) {
					if (strNums && strNums.lastIndexOf(lNum) !== -1) {
						link.classList.add("de-link-hid");
					}
					if (opNums.indexOf(lNum) !== -1) {
						link.classList.add("de-ref-op");
					}
					if (lPost.ref.indexOf(pNum) === -1) {
						lPost.ref.push(pNum);
						post.hasRef = true;
						if (Cfg.hideRefPsts && lPost.hidden) {
							if (!post.hidden) {
								post.hideRefs();
							}
							post.setVisib(true);
							post.setNote("reference to >>" + lNum);
						}
					} else {
						continue;
					}
				} else if (lPost.hasRef) {
					var ref = lPost.ref,
					    idx = ref.indexOf(pNum);
					if (idx === -1) {
						continue;
					}
					ref.splice(idx, 1);
					if (!ref.length) {
						lPost.hasRef = false;
						$del($c("de-refmap", lPost.el));
						continue;
					}
				}
				$del($c("de-refmap", lPost.el));
				addRefMap(lPost, "");
			}
		}
	}




	function Thread(el, prev, isLight) {
		var els = $Q(aib.qRPost, el),
		    len = els.length,
		    num = aib.getTNum(el),
		    omt = aib.t ? 1 : aib.getOmitted($q(aib.qOmitted, el), len);
		this.num = num;
		this.pcount = omt + len;
		this.el = el;
		this.prev = prev;
		if (prev) {
			prev.next = this;
		}
		var lastPost = this.op = el.post = new Post(aib.getOp(el), this, num, 0, true, prev ? prev.last : null, isLight);
		pByNum[num] = lastPost;
		for (var i = 0; i < len; i++) {
			var pEl = els[i];
			num = aib.getPNum(pEl);
			pByNum[num] = lastPost = new Post(pEl, this, num, omt + i, false, lastPost, isLight);
		}
		this.last = lastPost;
		if (isLight) {
			return;
		}
		el.style.counterReset = "de-cnt " + omt;
		el.removeAttribute("id");
		el.setAttribute("de-thread", null);
		visPosts = Math.max(visPosts, len);
		if (aib._420 || aib.tiny) {
			var temp = el.lastChild;
			if (temp !== this.op.el) {
				$after(el, temp);
			}
			$del($c("clear", el));
		}
	}
	Thread.clearPostsMark = function () {
		dForm.firstThr.clearPostsMarks();
	};
	Thread.prototype = Object.defineProperties({
		hasNew: false,
		hidden: false,
		loadedOnce: false,
		next: null,
		clearPostsMarks: function clearPostsMarks() {
			if (this.hasNew) {
				this.hasNew = false;
				$each($Q(".de-new-post", this.el), function (el) {
					el.classList.remove("de-new-post");
				});
				doc.removeEventListener("click", Thread.clearPostsMark, true);
			}
		},
		deletePost: function deletePost(post, delAll, removePost) {
			var count = 0,
			    idx = post.count;
			do {
				if (removePost) {
					$del(post.wrap);
					delete pByNum[post.num];
					if (post.hidden) {
						post.unhideRefs();
					}
					updRefMap(post, false);
					if (post.prev.next = post.next) {
						post.next.prev = post.prev;
					}
					if (this.last === post) {
						this.last = post.prev;
					}
				} else {
					post.deleted = true;
					post.btns.classList.remove("de-post-counter");
					post.btns.classList.add("de-post-deleted");
					post.wrap.classList.add("de-post-removed");
					($q("input[type=\"checkbox\"]", post.el) || {}).disabled = true;
				}
				post = post.nextNotDeleted;
				count++;
			} while (delAll && post);
			if (!spells.hasNumSpell) {
				sVis.splice(idx, count);
			}
			for (var tPost = post; tPost; tPost = tPost.nextInThread) {
				tPost.count -= count;
			}
			this.pcount -= count;
			return post;
		},
		load: function load(last, smartScroll) {
			var _this = this;

			var informUser = arguments[2] === undefined ? true : arguments[2];

			if (informUser) {
				$alert(Lng.loading[lang], "load-thr", true);
			}
			return ajaxLoad(aib.getThrdUrl(aib.b, this.num)).then(function (form) {
				return _this.loadFromForm(last, smartScroll, form);
			}, function (e) {
				return $alert(getErrorMessage(e), "load-thr", false);
			});
		},
		loadFromForm: function loadFromForm(last, smartScroll, form) {
			var _this = this;

			var nextCoord,
			    els = $Q(aib.qRPost, form),
			    op = this.op,
			    thrEl = this.el,
			    expEl = $c("de-collapse", thrEl),
			    nOmt = last === 1 ? 0 : Math.max(els.length - last, 0);
			if (smartScroll) {
				if (this.next) {
					nextCoord = this.next.topCoord;
				} else {
					smartScroll = false;
				}
			}
			pr.closeQReply();
			$del($q(aib.qOmitted + ", .de-omitted", thrEl));
			if (!this.loadedOnce) {
				if (op.trunc) {
					op.updateMsg(replacePost($q(aib.qMsg, form)));
				}
				op.ref = [];
				this.loadedOnce = true;
			}
			this._checkBans(op, form);
			this._parsePosts(els);
			thrEl.style.counterReset = "de-cnt " + (nOmt + 1);
			if (this._processExpandThread(els, last === 1 ? els.length : last)) {
				$del(expEl);
			} else if (!expEl) {
				thrEl.insertAdjacentHTML("beforeend", "<span class=\"de-collapse\">&gt;&gt; [" + "<a class=\"de-abtn\" href=\"" + aib.getThrdUrl(aib.b, this.num) + aib.anchor + this.last.num + "\">" + Lng.collapseThrd[lang] + "</a>]</span>");
				thrEl.lastChild.onclick = function (e) {
					$pd(e);
					_this.load(visPosts, true);
				};
			} else if (expEl !== thrEl.lastChild) {
				thrEl.appendChild(expEl);
			}
			if (nOmt !== 0) {
				op.el.insertAdjacentHTML("afterend", "<div class=\"de-omitted\">" + nOmt + "</div>");
			}
			if (smartScroll) {
				scrollTo(window.pageXOffset, window.pageYOffset - (nextCoord - this.next.topCoord));
			}
			closeAlert($id("de-alert-load-thr"));
		},
		loadNew: function loadNew(useAPI) {
			var _this = this;

			if (aib.dobr && useAPI) {
				return getJsonPosts("/api/thread/" + aib.b + "/" + aib.t + ".json").then(function (json) {
					if (json) {
						if (json.error) {
							return Promise.reject(new AjaxError(0, json.message));
						}
						if (_this._lastModified !== json.last_modified || _this.pcount !== json.posts_count) {
							_this._lastModified = json.last_modified;
							return _this.loadNew(false);
						}
					}
					return 0;
				});
			}
			return ajaxLoad(aib.getThrdUrl(aib.b, aib.t), true, !aib.dobr).then(function (form) {
				return form ? _this.loadNewFromForm(form) : 0;
			});
		},
		loadNewFromForm: function loadNewFromForm(form) {
			this._checkBans(dForm.firstThr.op, form);
			var lastOffset = pr.isVisible ? pr.topCoord : null;
			var _parsePosts = this._parsePosts($Q(aib.qRPost, form));

			var _parsePosts2 = _slicedToArray(_parsePosts, 2);

			var newPosts = _parsePosts2[0];
			var newVisPosts = _parsePosts2[1];

			if (lastOffset !== null) {
				scrollTo(window.pageXOffset, window.pageYOffset - (lastOffset - pr.topCoord));
			}
			if (newPosts !== 0) {
				$id("de-panel-info").firstChild.textContent = this.pcount + "/" + $Q(aib.qThumbImages, dForm.el).length;
			}
			return newVisPosts;
		},
		setFavBtn: function setFavBtn(state) {
			var el = $c(state ? "de-btn-fav" : "de-btn-fav-sel", this.op.btns);
			if (el) {
				el.className = state ? "de-btn-fav-sel" : "de-btn-fav";
				el.title = state ? Lng.delFav[lang] : Lng.addFav[lang];
			}
		},
		setFavorState: function setFavorState(val) {
			var _this = this;

			this.setFavBtn(val);
			readFav().then(function (fav) {
				var b = aib.b,
				    h = aib.host;
				if (val) {
					if (!fav[h]) {
						fav[h] = {};
					}
					if (!fav[h][b]) {
						fav[h][b] = {};
					}
					fav[h][b].url = aib.prot + "//" + aib.host + aib.getPageUrl(b, 0);
					fav[h][b][_this.num] = {
						cnt: _this.pcount,
						"new": 0,
						txt: _this.op.title,
						url: aib.getThrdUrl(b, _this.num),
						last: _this.last.num
					};
				} else {
					removeFavoriteEntry(fav, h, b, _this.num, false);
				}
				saveFavorites(fav);
			});
		},
		updateHidden: function updateHidden(data) {
			var date = Date.now(),
			    thr = this;
			do {
				var realHid = (thr.num in data);
				if (thr.hidden ^ realHid) {
					if (realHid) {
						thr.op.setUserVisib(true, date, false);
						data[thr.num] = thr.op.title;
					} else if (thr.hidden) {
						thr.op.setUserVisib(false, date, false);
					}
				}
			} while (thr = thr.next);
		},

		_lastModified: "",
		_addPost: function _addPost(parent, el, i, vParser, prev) {
			var post,
			    num = aib.getPNum(el),
			    wrap = aib.getWrap(el, false);
			el = replacePost(el);
			pByNum[num] = post = new Post(el, this, num, i, false, prev);
			Object.defineProperty(post, "wrap", { value: wrap });
			parent.appendChild(wrap);
			if (aib.t && updater.focused && Cfg.animation) {
				nav.animEvent(post.el, function (node) {
					node.classList.remove("de-post-new");
				});
				post.el.classList.add("de-post-new");
			}
			if (vParser) {
				vParser.parse(post);
			}
			processImageNames(el);
			post.addFuncs();
			preloadImages(el);
			if (aib.t && Cfg.markNewPosts) {
				this._addPostMark(el);
			}
			return post;
		},
		_addPostMark: function _addPostMark(postEl) {
			if (updater.focused) {
				this.clearPostsMarks();
			} else {
				if (!this.hasNew) {
					this.hasNew = true;
					doc.addEventListener("click", Thread.clearPostsMark, true);
				}
				postEl.classList.add("de-new-post");
			}
		},
		_checkBans: function _checkBans(op, thrNode) {
			if (!aib.qBan) {
				return;
			}
			var bEls = $Q(aib.qBan, thrNode);
			for (var i = 0, len = bEls.length; i < len; ++i) {
				var bEl = bEls[i],
				    pEl = aib.getPostEl(bEl),
				    post = pEl ? pByNum[aib.getPNum(pEl)] : op;
				if (post && !post.banned) {
					if (!$q(aib.qBan, post.el)) {
						post.msg.appendChild(bEl);
					}
					post.banned = true;
				}
			}
		},
		_importPosts: function _importPosts(last, newPosts, begin, end, vParser, sRunner) {
			var newCount = end - begin,
			    newVisCount = newCount,
			    fragm = doc.createDocumentFragment();
			for (; begin < end; ++begin) {
				last = this._addPost(fragm, newPosts[begin], begin + 1, vParser, last);
				newVisCount -= sRunner.run(last);
			}
			return [newCount, newVisCount, fragm, last];
		},
		_parsePosts: function _parsePosts(nPosts) {
			var _this = this;

			var vParser,
			    sRunner = new SpellsRunner(),
			    newPosts = 0,
			    newVisPosts = 0,
			    len = nPosts.length,
			    post = this.lastNotDeleted;
			if (aib.dobr || post.count !== 0 && (post.count > len || aib.getPNum(nPosts[post.count - 1]) !== post.num)) {
				if (Cfg.addYouTube) {
					vParser = new VideosParser();
				}
				post = this.op.nextNotDeleted;
				var i,
				    firstChangedPost = null;
				for (i = post.count - 1; i < len && post;) {
					if (post.num === aib.getPNum(nPosts[i])) {
						i++;
						post = post.nextNotDeleted;
						continue;
					}
					if (+post.num > +aib.getPNum(nPosts[i])) {
						if (!firstChangedPost) {
							firstChangedPost = post.prev;
						}
						var cnt = 0;
						do {
							cnt++;
							i++;
						} while (+aib.getPNum(nPosts[i]) < +post.num);
						var res = this._importPosts(post.prev, nPosts, i - cnt, i, vParser, sRunner);
						newPosts += res[0];
						this.pcount += res[0];
						newVisPosts += res[1];
						$after(post.prev.wrap, res[2]);
						res[3].next = post;
						post.prev = res[3];
						for (var temp = post; temp; temp = temp.nextInThread) {
							temp.count += cnt;
						}
					} else {
						if (!firstChangedPost) {
							firstChangedPost = post;
						}
						post = this.deletePost(post, false, !aib.t);
					}
				}
				if (i === len && post) {
					this.deletePost(post, true, !aib.t);
				}
				if (firstChangedPost && sRunner.hasNumSpell) {
					disableSpells();
					for (post = firstChangedPost.nextInThread; post; post = post.nextInThread) {
						sRunner.run(post);
					}
				}
				if (newPosts !== 0) {
					for (post = firstChangedPost; post; post = post.nextInThread) {
						updRefMap(post, true);
					}
				}
			}
			if (len + 1 > this.pcount) {
				if (Cfg.addYouTube && !vParser) {
					vParser = new VideosParser();
				}
				var res = this._importPosts(this.last, nPosts, this.lastNotDeleted.count, len, vParser, sRunner);
				newPosts += res[0];
				newVisPosts += res[1];
				this.el.appendChild(res[2]);
				this.last = res[3];
				this.pcount = len + 1;
			}
			readFav().then(function (fav) {
				var f = fav[aib.host];
				if (!f || !f[aib.b]) {
					return;
				}
				if (f = f[aib.b][_this.op.num]) {
					var el = $id("de-content-fav");
					if (el) {
						el = $q(".de-fav-current > .de-entry[de-num=\"" + _this.op.num + "\"] .de-fav-inf-old", el);
						el.textContent = _this.pcount;
						el = el.nextElementSibling;
						el.style.display = "none";
						el.textContent = 0;
					}
					f.cnt = _this.pcount;
					f["new"] = 0;
					f.last = _this.last.num;
					setStored("DESU_Favorites", JSON.stringify(fav));
				}
			});
			if (vParser) {
				vParser.end();
			}
			sRunner.end();
			return [newPosts, newVisPosts];
		},
		_processExpandThread: function _processExpandThread(nPosts, num) {
			var needRMUpdate,
			    post = this.op.next,
			    vPosts = this.pcount === 1 ? 0 : this.pcount - post.count;
			if (vPosts > num) {
				while (vPosts-- !== num) {
					post.wrap.classList.add("de-hidden");
					post.omitted = true;
					post = post.next;
				}
				needRMUpdate = false;
			} else if (vPosts < num) {
				var vParser,
				    fragm = doc.createDocumentFragment(),
				    tPost = this.op,
				    len = nPosts.length - vPosts;
				if (Cfg.addYouTube) {
					vParser = new VideosParser();
				}
				var sRunner = new SpellsRunner(false);
				for (var i = Math.max(0, len - num + vPosts); i < len; ++i) {
					tPost = this._addPost(fragm, nPosts[i], i + 1, vParser, tPost);
					sRunner.run(tPost);
				}
				sRunner.end();
				if (vParser) {
					vParser.end();
				}
				$after(this.op.wrap, fragm);
				tPost.next = post;
				if (post) {
					post.prev = tPost;
				}
				needRMUpdate = true;
				num = Math.min(len + vPosts, num);
			} else {
				return num <= visPosts;
			}
			while (vPosts-- !== 0) {
				if (post.trunc) {
					post.updateMsg(replacePost($q(aib.qMsg, nPosts[post.count - 1])));
				}
				if (post.omitted) {
					post.wrap.classList.remove("de-hidden");
					post.omitted = false;
				}
				if (needRMUpdate) {
					updRefMap(post, true);
				}
				post = post.next;
			}
			return num <= visPosts;
		}
	}, {
		lastNotDeleted: {
			get: function () {
				var post = this.last;
				while (post.deleted) {
					post = post.prev;
				}
				return post;
			},
			configurable: true,
			enumerable: true
		},
		nextNotHidden: {
			get: function () {
				for (var thr = this.next; thr && thr.hidden; thr = thr.next) {}
				return thr;
			},
			configurable: true,
			enumerable: true
		},
		prevNotHidden: {
			get: function () {
				for (var thr = this.prev; thr && thr.hidden; thr = thr.prev) {}
				return thr;
			},
			configurable: true,
			enumerable: true
		},
		topCoord: {
			get: function () {
				return this.op.topCoord;
			},
			configurable: true,
			enumerable: true
		}
	});




	function checkStorage() {
		try {
			locStorage = window.localStorage;
			sesStorage = window.sessionStorage;
			sesStorage["__de-test"] = 1;
		} catch (e) {
			if (typeof unsafeWindow !== "undefined") {
				locStorage = unsafeWindow.localStorage;
				sesStorage = unsafeWindow.sessionStorage;
			}
		}
		if (!(locStorage && typeof locStorage === "object" && sesStorage)) {
			console.log("WEBSTORAGE ERROR: please, enable webstorage!");
			return false;
		}
		return true;
	}

	function initNavFuncs() {
		if (!("includes" in String.prototype)) {
			String.prototype.includes = String.prototype.contains || function (s) {
				return this.indexOf(s) !== -1;
			};
		}
		if (!("startsWith" in String.prototype)) {
			String.prototype.startsWith = function (s) {
				return this.indexOf(s) === 0;
			};
		}
		if (!("repeat" in String.prototype)) {
			String.prototype.repeat = function (nTimes) {
				return new Array(nTimes + 1).join(this.valueOf());
			};
		}
		if (!("clz32" in Math)) {
			Math.clz32 = function (x) {
				return x < 1 ? x === 0 ? 32 : 0 : 31 - (Math.log(x) / Math.LN2 >> 0);
			};
		}
		if (!("assign" in Object)) {
			Object.assign = function (a, b) {
				for (var i in b) {
					a[i] = b[i];
				}
				return a;
			};
		}
		if ("toJSON" in aProto) {
			delete aProto.toJSON;
		}
		try {
			new File([""], "");
		} catch (e) {
			window.File = function File(arr, name) {
				var rv = new Blob(arr);
				rv.name = name;
				rv.lastModifiedDate = new Date();
				rv.__proto__ = File.prototype;
				return rv;
			};
			File.prototype = new Blob();
			var origAppend = FormData.prototype.append;
			FormData.prototype.append = function append(name, value) {
				var fileName = arguments[2] === undefined ? null : arguments[2];

				if (value instanceof Blob && "name" in value && fileName === null) {
					return origAppend.call(this, name, value, value.name);
				}
				return origAppend.apply(this, arguments);
			};
		}
		var ua = window.navigator.userAgent,
		    firefox = ua.includes("Gecko/"),
		    presto = window.opera ? +window.opera.version() : 0,
		    opera11 = presto ? presto < 12.1 : false,
		    webkit = ua.includes("WebKit/"),
		    chrome = webkit && ua.includes("Chrome/"),
		    safari = webkit && !chrome,
		    isGM = false,
		    isChromeStorage = window.chrome && !!window.chrome.storage,
		    isScriptStorage = !!scriptStorage && !ua.includes("Opera Mobi");
		try {
			isGM = typeof GM_setValue === "function" && (!chrome || !GM_setValue.toString().includes("not supported"));
		} catch (e) {}
		nav = Object.defineProperties({
			Firefox: firefox,
			Opera11: opera11,
			Presto: presto,
			WebKit: webkit,
			Chrome: chrome,
			Safari: safari,
			isGM: isGM,
			isChromeStorage: isChromeStorage,
			isScriptStorage: isScriptStorage,
			isGlobal: isGM || isChromeStorage || isScriptStorage,
			scriptInstall: firefox ? typeof GM_info !== "undefined" ? "Greasemonkey" : "Scriptish" : isChromeStorage ? "Chrome extension" : isGM ? "Monkey" : "Native userscript",
			cssFix: webkit ? "-webkit-" : opera11 ? "-o-" : "",
			Anim: !opera11,
			animName: webkit ? "webkitAnimationName" : opera11 ? "OAnimationName" : "animationName",
			animEnd: webkit ? "webkitAnimationEnd" : opera11 ? "oAnimationEnd" : "animationend",
			animEvent: function animEvent(el, fn) {
				el.addEventListener(this.animEnd, function aEvent() {
					this.removeEventListener(nav.animEnd, aEvent, false);
					fn(this);
					fn = null;
				}, false);
			},
			fixLink: safari ? getAbsLink : function fixLink(url) {
				return url;
			},
		
			getUnsafeUint8Array: function getUnsafeUint8Array(data) {
				var i = arguments[1] === undefined ? 0 : arguments[1];
				var len = arguments[2] === undefined ? null : arguments[2];

				var rv;
				if (len === null) {
					rv = new Uint8Array(data, i);
					return rv instanceof Uint8Array ? rv : new unsafeWindow.Uint8Array(data, i);
				}
				rv = new Uint8Array(data, i, len);
				return rv instanceof Uint8Array ? rv : new unsafeWindow.Uint8Array(data, i, len);
			},
			getUnsafeDataView: function getUnsafeDataView(data, offset) {
				var rv = new DataView(data, offset || 0);
				return rv instanceof DataView ? rv : new unsafeWindow.DataView(data, offset || 0);
			}
		}, {
			ua: {
				get: function () {
					return navigator.userAgent + (this.Firefox ? " [" + navigator.buildID + "]" : "");
				},
				configurable: true,
				enumerable: true
			},
			hasWorker: {
				get: function () {
					var val = false;
					try {
						val = "Worker" in window && "URL" in window;
					} catch (e) {}
					if (val && this.Firefox) {
						val = +(navigator.userAgent.match(/rv:(\d{2})/) || [])[1] >= 40;
					}
					Object.defineProperty(this, "hasWorker", { value: val });
					return val;
				},
				configurable: true,
				enumerable: true
			},
			canPlayMP3: {
				get: function () {
					var val = !!new Audio().canPlayType("audio/mpeg;");
					Object.defineProperty(this, "canPlayMP3", { value: val });
					return val;
				},
				configurable: true,
				enumerable: true
			},
			canPlayWebm: {
				get: function () {
					var val = !!new Audio().canPlayType("video/webm; codecs=\"vp8,vorbis\"");
					Object.defineProperty(this, "canPlayWebm", { value: val });
					return val;
				},
				configurable: true,
				enumerable: true
			},
			matchesSelector: {
				get: function () {
					var dE = doc.documentElement,
					    val = Function.prototype.call.bind(dE.matches || dE.mozMatchesSelector || dE.webkitMatchesSelector || dE.oMatchesSelector);
					Object.defineProperty(this, "matchesSelector", { value: val });
					return val;
				},
				configurable: true,
				enumerable: true
			}
		});
	}




	function getImageBoard(checkDomains, checkEngines) {
		var prot = window.location.protocol;
		var ibDomains = Object.defineProperties({
			"02ch.net": [{
				qPostRedir: { value: "input[name=\"gb2\"][value=\"thread\"]" },
				ru: { value: true },
				timePattern: { value: "yyyy+nn+dd++w++hh+ii+ss" }
			}],
			"2chru.net": [{
				_2chru: { value: true },

				css: { value: ".small { display: none; }" }
			}, "form[action*=\"imgboard.php?delete\"]"],
			"2--ch.ru": [{
				tire: { value: true },

				qPages: { value: "table[border=\"1\"] tr:first-of-type > td:first-of-type a" },
				qPostRedir: { value: null },
				qTable: { value: "table:not(.postfiles)" },
				qThread: { value: ".threadz" },
				getOmitted: { value: function value(el, len) {
						var txt;
						return el && (txt = el.textContent) ? +(txt.match(/\d+/) || [0])[0] - len : 1;
					} },
				getPageUrl: { value: function value(b, p) {
						return fixBrd(b) + (p > 0 ? p : 0) + ".memhtml";
					} },
				css: { value: "span[id$=\"_display\"], #fastload { display: none !important; }" },
				docExt: { value: ".html" },
				fixFileInputs: { value: function value(el) {
						var str = "><input name=\"file\" maxlength=\"4\" " + "accept=\"|sid|7z|bz2|m4a|flac|lzh|mo3|rar|spc|fla|nsf|jpg|mpp|aac|gz|xm|wav|" + "mp3|png|it|lha|torrent|swf|zip|mpc|ogg|jpeg|gif|mod\" type=\"file\"></input></div>";
						el.parentNode.innerHTML = "<div" + str + ("<div style=\"display: none;\"" + str).repeat(3);
					} },
				hasPicWrap: { value: true },
				markupBB: { value: true },
				multiFile: { value: true },
				ru: { value: true }
			}],
			"410chan.org": [{
				_410: { value: true },

				qPostRedir: { value: "input#noko" },
				getSage: { value: function value(post) {
						var el = $c("filetitle", post);
						return el && el.textContent.includes("⇩");
					} },
				css: { value: "#resizer { display: none !important; }" },
				markupBB: { value: false },
				markupTags: { value: ["**", "*", "__", "^^", "%%", "`", "", "", "q"] },
				timePattern: { value: "dd+nn+yyyy++w++hh+ii+ss" }
			}, "script[src*=\"kusaba\"]"],
			"420chan.org": [{
				_420: { value: true },

				qBan: { value: ".ban" },
				qError: { value: "pre" },
				qHide: { value: ".replyheader ~ *" },
				qPages: { value: ".pagelist > a:last-child" },
				qPostRedir: { value: null },
				qThread: { value: "[id*=\"thread\"]" },
				getTNum: { value: function value(op) {
						return $q("a[id]", op).id.match(/\d+/)[0];
					} },
				css: { value: "#content > hr, .hidethread, .ignorebtn, .opqrbtn, .qrbtn, noscript { display: none !important; }\t\t\t\t.de-thr-hid { margin: 1em 0; }" },
				docExt: { value: ".php" },
				markupBB: { value: true },
				markupTags: { value: ["**", "*", "", "", "%", "pre", "", "", "q"] }
			}],
			"4chan.org": [{
				fch: { value: true },

				cFileInfo: { value: "fileText" },
				cOPost: { value: "op" },
				cReply: { value: "post reply" },
				cSubj: { value: "subject" },
				qBan: { value: "strong[style=\"color: red;\"]" },
				qClosed: { value: ".archivedIcon" },
				qDelBut: { value: ".deleteform > input[type=\"submit\"]" },
				qError: { value: "#errmsg" },
				qHide: { value: ".postInfo ~ *" },
				qImgLink: { value: ".fileText > a" },
				qName: { value: ".name" },
				qOmitted: { value: ".summary.desktop" },
				qPages: { value: ".pagelist > .pages:not(.cataloglink) > a:last-of-type" },
				qPostForm: { value: "form[name=\"post\"]" },
				qPostRedir: { value: null },
				qRef: { value: ".postInfo > .postNum" },
				qTable: { value: ".replyContainer" },
				qThumbImages: { value: ".fileThumb > img" },
				getFileInfo: { value: function value(wrap) {
						var el = $c(this.cFileInfo, wrap);
						return el ? el.lastChild.textContent : "";
					} },
				getPageUrl: { value: function value(b, p) {
						return fixBrd(b) + (p > 1 ? p : "");
					} },
				getSage: { value: function value(post) {
						return !!$q(".id_Heaven, .useremail[href^=\"mailto:sage\"]", post);
					} },
				getTNum: { value: function value(op) {
						return $q("input[type=\"checkbox\"]", op).name.match(/\d+/)[0];
					} },
				getWrap: { value: function value(el, isOp) {
						return el.parentNode;
					} },
				anchor: { value: "#p" },
				css: { value: ".backlink, #blotter, .extButton, hr.desktop, .navLinks, .postMenuBtn, #togglePostFormLink { display: none !important; }\t\t\t\t.postForm { display: table !important; width: auto !important; }\t\t\t\ttextarea { margin-right: 0 !important; }" },
				docExt: { value: "" },
				firstPage: { value: 1 },
				init: { value: function value() {
						var el = $id("captchaFormPart");
						if (el) {
							doc.body.insertAdjacentHTML("beforeend", "<div style=\"display: none;\">" + "<div onclick=\"initRecaptcha();\"></div></div>");
							this.updateCaptcha = (function (el, focus) {
								$id("g-recaptcha").innerHTML = "";
								this.click();
								el.style.display = "";
							}).bind(doc.body.lastChild.firstChild, el);
						}
						return false;
					} },
				markupBB: { value: true },
				markupTags: { value: ["", "", "", "", "spoiler", "", "", "", "q"] },
				rLinkClick: { value: "" },
				rep: { value: true },
				res: { value: "thread/" },
				timePattern: { value: "nn+dd+yy+w+hh+ii-?s?s?" },
				thrid: { value: "resto" }
			}],
			"7chan.org": [{
				init: { value: function value() {
						return true;
					} }
			}],
			"8ch.net": [{
				_8ch: { value: true },

				css: { value: ".fileinfo { width: 250px; }\t\t\t\t.multifile { width: auto !important; }\t\t\t\t.post-btn { display: none !important; }" },
				earlyInit: { value: function value() {
						if (locStorage.file_dragdrop === "true") {
							locStorage.file_dragdrop = false;
							return true;
						}
						return false;
					} },
				fixFileInputs: { value: function value(el) {
						var str = "";
						for (var i = 0, len = 5; i < len; ++i) {
							str += "<div" + (i === 0 ? "" : " style=\"display: none;\"") + "><input type=\"file\" name=\"file" + (i === 0 ? "" : i + 1) + "\"></div>";
						}
						$id("upload_file").parentNode.innerHTML = str;
					} },
				multiFile: { value: true }
			}, "form[name*=\"postcontrols\"]"],
			"arhivach.org": [{
				cReply: { value: "post" },
				qDForm: { value: "body > .container-fluid" },
				qHide: { value: ".post_comment" },
				qMsg: { value: ".post_comment_body" },
				qRef: { value: ".post_id, .post_head > b" },
				qRPost: { value: ".post:not(:first-child):not([postid=\"\"])" },
				qThread: { value: ".thread_inner" },
				getOp: { value: function value(el) {
						return $q(".post:first-child", el);
					} },
				getPNum: { value: function value(post) {
						return post.getAttribute("postid");
					} },
				getTNum: { value: function value(el) {
						return this.getOp(el).getAttribute("postid");
					} },
				init: { value: function value() {
						setTimeout(function () {
							var delPosts = $Q(".post[postid=\"\"]", doc);
							for (var i = 0, len = delPosts.length; i < len; ++i) {
								try {
									var post = pByNum[$q("blockquote", delPosts[i]).getAttribute("id").substring(1)];
									if (post) {
										post.deleted = true;
										post.btns.classList.remove("de-post-counter");
										post.btns.classList.add("de-post-deleted");
										post.wrap.classList.add("de-post-removed");
									}
								} catch (e) {}
							}
						}, 0);
						return false;
					} },
				css: { value: ".post_replies, .post[postid=\"\"] { display: none !important; }\t\t\t\t.post { overflow-x: auto !important; }" },
				docExt: { value: "" },
				res: { value: "thread/" },
				rLinkClick: { value: "" }
			}],
			"diochan.com": [{
				dio: { value: true },

				css: { value: ".resize { display: none !important; }" }
			}, "script[src*=\"kusaba\"]"],
			"dobrochan.com": [{
				dobr: { value: true },

				anchor: { value: "#i" },
				cFileInfo: { value: "fileinfo" },
				cSubj: { value: "replytitle" },
				qClosed: { value: "img[src=\"/images/locked.png\"]" },
				qDForm: { value: "form[action*=\"delete\"]" },
				qError: { value: ".post-error, h2" },
				qMsg: { value: ".postbody" },
				qOmitted: { value: ".abbrev > span:last-of-type" },
				qPages: { value: ".pages > tbody > tr > td" },
				qPostRedir: { value: "select[name=\"goto\"]" },
				qTrunc: { value: ".abbrev > span:nth-last-child(2)" },
				getImgLink: { value: function value(img) {
						var el = img.parentNode;
						return el.tagName === "A" ? el : $q(".fileinfo > a", img.previousElementSibling ? el : el.parentNode);
					} },
				getImgWrap: { value: function value(el) {
						return el.tagName === "A" ? (el.previousElementSibling ? el : el.parentNode).parentNode : el.firstElementChild.tagName === "IMG" ? el.parentNode : el;
					} },
				getPageUrl: { value: function value(b, p) {
						return fixBrd(b) + (p > 0 ? p + this.docExt : "index.xhtml");
					} },
				getTNum: { value: function value(op) {
						return $q("a[name]", op).name.match(/\d+/)[0];
					} },
				insertYtPlayer: { value: function value(msg, playerHtml) {
						var prev = msg.previousElementSibling,
						    node = prev.tagName === "BR" ? prev : msg;
						node.insertAdjacentHTML("beforebegin", playerHtml);
						return node.previousSibling;
					} },
				css: { value: ".delete > img, .popup, .reply_, .search_google, .search_iqdb { display: none !important; }\t\t\t\t.delete { background: none; }\t\t\t\t.delete_checkbox { position: static !important; }\t\t\t\t.file + .de-video-obj { float: left; margin: 5px 20px 5px 5px; }\t\t\t\t.de-video-obj + div { clear: left; }" },
				disableRedirection: { value: function value(el) {
						($q(this.qPostRedir, el) || {}).selectedIndex = 1;
					} },
				fixFileInputs: { value: function value(el) {
						el = $id("files_parent");
						$each($Q("input[type=\"file\"]", el), function (el) {
							el.removeAttribute("onchange");
						});
						el.firstElementChild.value = 1;
					} },
				hasPicWrap: { value: true },
				hDTFix: { configurable: true, get: function get() {
						var val = new DateTime("yyyy-nn-dd-hh-ii-ss", "_d _M _Y (_w) _h:_i ", Cfg.timeOffset || 0, Cfg.correctTime ? lang : 1);
						Object.defineProperty(this, "weight", { value: val });
						return val;
					} },
				init: { value: function value() {
						if (window.location.pathname === "/settings") {
							if (!nav) {
								initNavFuncs();
							}
							$q("input[type=\"button\"]", doc).addEventListener("click", function () {
								spawn(readCfg).then(function () {
									return saveCfg("__hanarating", $id("rating").value);
								});
							}, false);
							return true;
						}
						return false;
					} },
				multiFile: { value: true },
				rLinkClick: { value: "onclick=\"Highlight(event, this.textContent.substr(2))\"" },
				ru: { value: true },
				timePattern: { value: "dd+m+?+?+?+?+?+yyyy++w++hh+ii-?s?s?" }
			}],
			"dva-ch.net": [{
				dvachnet: { value: true } }],
			"hii.pm": [{
				qPostRedir: { value: "input[name=\"redirecttothread\"][value=\"1\"]" },
				css: { value: "#captcha_status, .content-background > hr, div[style=\"position: relative;\"] { display: none !important; }" },
				ru: { value: true }
			}, "script[src*=\"kusaba\"]"],
			"iichan.hk": [{
				iich: { value: true }
			}],
			"inach.org": [{
				qPostRedir: { value: "input[name=\"fieldnoko\"]" },
				css: { value: "#postform > table > tbody > tr:first-child { display: none !important; }" },
				markupBB: { value: true },
				timePattern: { value: "nn+dd+yyyy++w++hh+ii+ss" }
			}],
			"krautchan.net": [{
				krau: { value: true },

				cFileInfo: { value: "fileinfo" },
				cReply: { value: "postreply" },
				cSubj: { value: "postsubject" },
				qBan: { value: ".ban_mark" },
				qClosed: { value: "img[src=\"/images/locked.gif\"]" },
				qDForm: { value: "form[action*=\"delete\"]" },
				qError: { value: ".message_text" },
				qHide: { value: "div:not(.postheader)" },
				qImgLink: { value: ".filename > a" },
				qOmitted: { value: ".omittedinfo" },
				qPages: { value: "table[border=\"1\"] > tbody > tr > td > a:nth-last-child(2) + a" },
				qPostRedir: { value: "input#forward_thread" },
				qRef: { value: ".postnumber" },
				qRPost: { value: ".postreply" },
				qThread: { value: ".thread_body" },
				qThumbImages: { value: "img[id^=\"thumbnail_\"]" },
				qTrunc: { value: "p[id^=\"post_truncated\"]" },
				getImgWrap: { value: function value(el) {
						return el.parentNode;
					} },
				getSage: { value: function value(post) {
						return !!$c("sage", post);
					} },
				getTNum: { value: function value(op) {
						return $q("input[type=\"checkbox\"]", op).name.match(/\d+/)[0];
					} },
				insertYtPlayer: { value: function value(msg, playerHtml) {
						var pMsg = msg.parentNode,
						    prev = pMsg.previousElementSibling,
						    node = prev.hasAttribute("style") ? prev : pMsg;
						node.insertAdjacentHTML("beforebegin", playerHtml);
						return node.previousSibling;
					} },
				css: { value: "img[id^=\"translate_button\"], img[src$=\"button-expand.gif\"], img[src$=\"button-close.gif\"], body > center > hr, form > div:first-of-type > hr, h2, .sage { display: none !important; }\t\t\t\tdiv[id^=\"Wz\"] { z-index: 10000 !important; }\t\t\t\t.de-thr-hid { float: none !important; }\t\t\t\t.file_reply + .de-video-obj, .file_thread + .de-video-obj { margin: 5px 20px 5px 5px; float: left; }\t\t\t\t.de-video-obj + div { clear: left; }\t\t\t\tform[action=\"/paint\"] > select { width: 105px; }\t\t\t\tform[action=\"/paint\"] > input[type=\"text\"] { width: 24px !important; }" },
				fixFileInputs: { value: function value(el) {
						var str = "";
						for (var i = 0, len = 4; i < len; ++i) {
							str += "<div" + (i === 0 ? "" : " style=\"display: none;\"") + "><input type=\"file\" name=\"file_" + i + "\" tabindex=\"7\"></input></div>";
						}
						var node = $id("files_parent");
						node.innerHTML = str;
						node.removeAttribute("id");
					} },
				hasPicWrap: { value: true },
				init: { value: function value() {
						doc.body.insertAdjacentHTML("beforeend", "<div style=\"display: none;\">" + "<div onclick=\"window.lastUpdateTime = 0;\"></div>" + "<div onclick=\"if(boardRequiresCaptcha) { requestCaptcha(true); }\"></div>" + "<div onclick=\"setupProgressTracking();\"></div>" + "</div>");
						var els = doc.body.lastChild.children;
						this.btnZeroLUTime = els[0];
						this.initCaptcha = els[1];
						this.addProgressTrack = els[2];
						return false;
					} },
				markupBB: { value: true },
				markupTags: { value: ["b", "i", "u", "s", "spoiler", "aa", "", "", "q"] },
				multiFile: { value: true },
				rep: { value: true },
				res: { value: "thread-" },
				rLinkClick: { value: "onclick=\"highlightPost(this.textContent.substr(2))\"" },
				timePattern: { value: "yyyy+nn+dd+hh+ii+ss+--?-?-?-?-?" }
			}],
			"mlpg.co": [{
				cOPost: { value: "opContainer" },
				getWrap: { value: function value(el, isOp) {
						return el.parentNode;
					} },
				css: { value: ".image-hover, form > div[style=\"text-align: center;\"], form > div[style=\"text-align: center;\"] + hr { display: none !important; }" },
				markupBB: { value: true },
				markupTags: { value: ["b", "i", "u", "-", "spoiler", "c", "", "", "q"] }
			}, "form[name*=\"postcontrols\"]"],
			"ponya.ch": [{
				ponya: { value: true },

				multiFile: { value: true },
				thrid: { value: "replythread" }
			}],
			"ponychan.net": [{
				cOPost: { value: "opContainer" },
				css: { value: ".mature_thread { display: block !important; }\t\t\t\t.mature_warning { display: none; }" }
			}, "form[name*=\"postcontrols\"]"],
			"syn-ch.ru": [{
				synch: { value: true },

				cFileInfo: { value: "unimportant" },
				css: { value: ".fa-sort, .image_id { display: none !important; }\t\t\t\ttime:after { content: none; }" },
				earlyInit: { value: function value() {
						var val = "{\"simpleNavbar\":true,\"showInfo\":true}";
						if (locStorage.settings !== val) {
							locStorage.settings = val;
							return true;
						}
						return false;
					} },
				markupBB: { value: true },
				markupTags: { value: ["b", "i", "u", "s", "spoiler", "code", "sub", "sup", "q"] }
			}, "form[name*=\"postcontrols\"]"] }, {
			"2-chru.net": {
				get: function () {
					return this["2chru.net"];
				},
				configurable: true,
				enumerable: true
			},
			"2-ch.su": {
				get: function () {
					return this["2--ch.ru"];
				},
				configurable: true,
				enumerable: true
			},
			"2ch.hk": {
				get: function () {
					return [ibEngines["body.makaba"]];
				},
				configurable: true,
				enumerable: true
			},
			"2ch.pm": {
				get: function () {
					return [ibEngines["body.makaba"]];
				},
				configurable: true,
				enumerable: true
			},
			"dmirrgetyojz735v.onion": {
				get: function () {
					return this["2chru.net"];
				},
				configurable: true,
				enumerable: true
			},
			"dobrochan.org": {
				get: function () {
					return this["dobrochan.com"];
				},
				configurable: true,
				enumerable: true
			},
			"dobrochan.ru": {
				get: function () {
					return this["dobrochan.com"];
				},
				configurable: true,
				enumerable: true
			},
			"niuchan.org": {
				get: function () {
					return this["diochan.com"];
				},
				configurable: true,
				enumerable: true
			},
			"ponyach.cf": {
				get: function () {
					return this["ponya.ch"];
				},
				configurable: true,
				enumerable: true
			},
			"ponyach.ga": {
				get: function () {
					return this["ponya.ch"];
				},
				configurable: true,
				enumerable: true
			},
			"ponyach.ml": {
				get: function () {
					return this["ponya.ch"];
				},
				configurable: true,
				enumerable: true
			},
			"ponyach.ru": {
				get: function () {
					return this["ponya.ch"];
				},
				configurable: true,
				enumerable: true
			},
			"ponychan.ru": {
				get: function () {
					return this["ponya.ch"];
				},
				configurable: true,
				enumerable: true
			},
			"syn-ch.com": {
				get: function () {
					return this["syn-ch.ru"];
				},
				configurable: true,
				enumerable: true
			},
			"syn-ch.org": {
				get: function () {
					return this["syn-ch.ru"];
				},
				configurable: true,
				enumerable: true
			}
		});

		var ibEngines = Object.defineProperties({
			"body.makaba": {
				mak: { value: true },

				cReply: { value: "post reply" },
				cSubj: { value: "post-title" },
				qBan: { value: ".pomyanem" },
				qClosed: { value: ".sticky-img[src$=\"locked.png\"]" },
				qDForm: { value: "#posts-form" },
				qHide: { value: ".post-details ~ *" },
				qImgLink: { value: ".file-attr > .desktop" },
				qMsg: { value: ".post-message" },
				qName: { value: ".ananimas, .post-email" },
				qOmitted: { value: ".mess-post" },
				qPostRedir: { value: null },
				qRPost: { value: "div.reply" },
				qThumbImages: { value: ".preview" },
				qTrunc: { value: null },
				nameSelector: { value: ".ananimas, .post-email, .ananimas > span, .post-email > span" },
				getImgParent: { value: function value(node) {
						var el = $parent(node, "FIGURE"),
						    parent = el.parentNode;
						return parent.lastElementChild === el ? parent : el;
					} },
				getImgWrap: { value: function value(el) {
						return $parent(el, "FIGURE");
					} },
				getPNum: { value: function value(post) {
						return post.getAttribute("data-num");
					} },
				getSage: { writable: true, value: function value(post) {
						if (this.hasNames) {
							this.getSage = function (post) {
								var name = $q(this.qName, post);
								return name ? name.childElementCount === 0 && !$c("ophui", post) : false;
							};
						} else {
							this.getSage = Object.getPrototypeOf(this).getSage;
						}
						return this.getSage(post);
					} },
				getWrap: { value: function value(el) {
						return el.parentNode;
					} },
				cssEn: { get: function get() {
						return ".ABU-refmap, .box[onclick=\"ToggleSage()\"], img[alt=\"webm file\"], .de-qarea-hanging .kupi-passcode-suka, .fa-media-icon, header > :not(.logo) + hr, .media-expand-button, .news, .norm-reply, .message-byte-len, .postform-hr, .postpanel > :not(img), .posts > hr, .reflink:before, .thread-nav, #ABU-alert-wait, #media-thumbnail { display: none !important; }\n\t\t\t\t.captcha-image > img { cursor: pointer; }\n\t\t\t\t.de-abtn { transition: none; }\n\t\t\t\t#de-txt-panel { font-size: 16px !important; }\n\t\t\t\t.images-area input { float: none !important; display: inline !important; }\n\t\t\t\t.images-single + .de-video-obj { display: inline-block; }\n\t\t\t\t.mess-post { display: block; }\n\t\t\t\t.postbtn-reply-href { font-size: 0px; }\n\t\t\t\t.postbtn-reply-href::after { font-size: 14px; content: attr(name); }\n\t\t\t\t" + (Cfg.expandTrunc ? ".expand-large-comment, div[id^=\"shrinked-post\"] { display: none !important; } div[id^=\"original-post\"] { display: block !important; }" : "") + "\n\t\t\t\t" + (Cfg.delImgNames ? ".filesize { display: inline !important; }" : "");
					} },
				earlyInit: { value: function value() {
						try {
							var obj = JSON.parse(locStorage.store);
							if (obj.other.navigation !== "page") {
								obj.other.navigation = "page";
								locStorage.store = JSON.stringify(obj);
								return true;
							}
						} catch (e) {}
						return false;
					} },
				hasNames: { configurable: true, get: function get() {
						var val = !!$q(".ananimas > span[id^=\"id_tag_\"], .post-email > span[id^=\"id_tag_\"]", doc.body);
						Object.defineProperty(this, "hasNames", { value: val });
						return val;
					} },
				hasPicWrap: { value: true },
				init: { value: function value() {
						var _this = this;

						$script("window.FormData = void 0;");
						$each($C("autorefresh", doc), $del);
						var el = $q("td > .anoniconsselectlist", doc);
						if (el) {
							$q(".option-area > td:last-child", doc).appendChild(el);
						}
						el = $q("tr:not([class])", doc.body);
						if (!el) {
							return false;
						}
						doc.body.insertAdjacentHTML("beforeend", "<div style=\"display: none;\">" + "<div onclick=\"loadCaptcha();\"></div></div>");
						this.updateCaptcha = (function (el, focus) {
							this.click();
							el.style.display = "";
							el = $id("captcha-value");
							if (el) {
								pr.cap = el;
								el.tabIndex = 999;
								if (focus) {
									el.focus();
								}
							}
						}).bind(doc.body.lastChild.firstChild, el);
						el.addEventListener("click", function (e) {
							if (e.target.tagName === "IMG") {
								_this.updateCaptcha(true);
								e.stopPropagation();
							}
						}, true);
						return false;
					} },
				fixFileInputs: { value: function value(el) {
						var str = "";
						for (var i = 0, len = 4; i < len; ++i) {
							str += "<div" + (i === 0 ? "" : " style=\"display: none;\"") + "><input type=\"file\" name=\"image" + (i + 1) + "\"></input></div>";
						}
						$q("#postform .images-area", doc).lastElementChild.innerHTML = str;
					} },
				lastPage: { configurable: true, get: function get() {
						var els = $Q(".pager > a:not([class])", doc),
						    val = els ? els.length : 1;
						Object.defineProperty(this, "lastPage", { value: val });
						return val;
					} },
				markupBB: { value: true },
				markupTags: { value: ["B", "I", "U", "S", "SPOILER", "CODE", "SUP", "SUB", "q"] },
				multiFile: { value: true },
				rLinkClick: { value: "" },
				timePattern: { value: "dd+nn+yy+w+hh+ii+ss" }
			},
			"form[action*=\"futaba.php\"]": {
				futa: { value: true },

				qDForm: { value: "form:not([enctype])" },
				qImgLink: { value: "a[href$=\".jpg\"], a[href$=\".png\"], a[href$=\".gif\"]" },
				qOmitted: { value: "font[color=\"#707070\"]" },
				qPostForm: { value: "form:nth-of-type(1)" },
				qPostRedir: { value: null },
				qRef: { value: ".del" },
				qRPost: { value: "td:nth-child(2)" },
				qThumbImages: { value: "a[href$=\".jpg\"] > img, a[href$=\".png\"] > img, a[href$=\".gif\"] > img" },
				getPageUrl: { value: function value(b, p) {
						return fixBrd(b) + (p > 0 ? p + this.docExt : "futaba.htm");
					} },
				getPNum: { value: function value(post) {
						return $t("input", post).name;
					} },
				getPostEl: { value: function value(el) {
						while (el && el.tagName !== "TD" && !el.hasAttribute("de-thread")) {
							el = el.parentElement;
						}
						return el;
					} },
				getTNum: { value: function value(op) {
						return $q("input[type=\"checkbox\"]", op).name.match(/\d+/)[0];
					} },
				cssEn: { value: ".de-cfg-body, .de-content { font-family: arial; }\t\t\t\t.ftbl { width: auto; margin: 0; }\t\t\t\t.reply { background: #f0e0d6; }\t\t\t\tspan { font-size: inherit; }" },
				docExt: { value: ".htm" },
				thrid: { value: "resto" }
			},
			"form[action*=\"imgboard.php?delete\"]": {
				tinyIb: { value: true },

				qPostRedir: { value: null },
				ru: { value: true }
			},
			"form[name*=\"postcontrols\"]": {
				tiny: { value: true },

				cFileInfo: { value: "fileinfo" },
				cReply: { value: "post reply" },
				qClosed: { value: ".fa-lock" },
				cSubj: { value: "subject" },
				cTrip: { value: "trip" },
				qDForm: { value: "form[name*=\"postcontrols\"]" },
				qHide: { value: ".intro ~ *" },
				qImgLink: { value: "p.fileinfo > a:first-of-type" },
				qMsg: { value: ".body" },
				qName: { value: ".name" },
				qOmitted: { value: ".omitted" },
				qPages: { value: ".pages > a:nth-last-of-type(2)" },
				qPostForm: { value: "form[name=\"post\"]" },
				qPostRedir: { value: null },
				qRef: { value: ".post_no + a" },
				qTable: { value: ".post.reply" },
				qTrunc: { value: ".toolong" },
				fixVideo: { value: function value(post) {
						var videos = [],
						    els = $Q(".video-container, #ytplayer", post ? post.el : dForm.el);
						for (var i = 0, len = els.length; i < len; ++i) {
							var el = els[i];
							videos.push([post || this.getPostEl(el).post, el.id === "ytplayer" ? el.src.match(Videos.ytReg) : ["", el.getAttribute("data-video")], true]);
							$del(el);
						}
						return videos;
					} },
				getPageUrl: { value: function value(b, p) {
						return p > 1 ? fixBrd(b) + p + this.docExt : fixBrd(b);
					} },
				getTNum: { value: function value(op) {
						return $q("input[type=\"checkbox\"]", op).name.match(/\d+/)[0];
					} },
				firstPage: { value: 1 },
				markupTags: { value: ["'''", "''", "__", "^H", "**", "`", "", "", "q"] },
				cssEn: { get: function get() {
						return ".banner, " + (this.t ? "" : ".de-btn-rep,") + " .hide-thread-link, .mentioned, .post-hover { display: none !important; }\n\t\t\t\t\tdiv.post.reply { float: left; clear: left; display: block; }\n\t\t\t\t\tform, form table { margin: 0; }";
					} },
				rLinkClick: { value: "onclick=\"highlightReply(this.textContent.substr(2))\"" },
				timePattern: { value: "nn+dd+yy++w++hh+ii+ss" },
				thrid: { value: "thread" }
			},
			"script[src*=\"kusaba\"]": {
				kus: { value: true },

				cOPost: { value: "postnode" },
				qError: { value: "h1, h2, div[style*=\"1.25em\"]" },
				qPostRedir: { value: null },
				cssEn: { value: ".extrabtns > a, .extrabtns > span, #newposts_get, .replymode, .ui-resizable-handle, blockquote + a { display: none !important; }\t\t\t\t.ui-wrapper { display: inline-block; width: auto !important; height: auto !important; padding: 0 !important; }" },
				markupBB: { value: true },
				rLinkClick: { value: "onclick=\"highlight(this.textContent.substr(2), true)\"" }
			},
			"link[href$=\"phutaba.css\"]": {
				cOPost: { value: "thread_OP" },
				cReply: { value: "post" },
				cSubj: { value: "subject" },
				cTrip: { value: "tripcode" },
				qError: { value: ".error" },
				qHide: { value: ".post > .post_body" },
				qImgLink: { value: ".filename > a" },
				qMsg: { value: ".text" },
				qPages: { value: ".pagelist > li:nth-last-child(2)" },
				qPostRedir: { value: "input[name=\"gb2\"][value=\"thread\"]" },
				qRPost: { value: ".thread_reply" },
				qTrunc: { value: ".tldr" },
				getImgWrap: { value: function value(el) {
						return el.parentNode.parentNode;
					} },
				getSage: { value: function value(post) {
						return !!$q(".sage", post);
					} },
				cssEn: { value: ".content > hr, .de-parea > hr { display: none !important }" },
				docExt: { value: "" },
				fixFileInputs: { value: function value(el) {
						var str = "><input name=\"file\" type=\"file\"></input></div>";
						el.removeAttribute("onchange");
						el.parentNode.parentNode.innerHTML = "<div" + str + ("<div style=\"display: none;\"" + str).repeat(3);
					} },
				markupBB: { value: true },
				multiFile: { value: true },
				res: { value: "thread/" },
				rLinkClick: { value: "" }
			},
			"div#mainc": {
				qDForm: { value: "#mainc" },
				getPageUrl: { value: function value(b, p) {
						return fixBrd(b) + "?do=page&p=" + (p < 0 ? 0 : p);
					} },
				getThrdUrl: { value: function value(b, tNum) {
						return this.prot + "//" + this.host + fixBrd(b) + "?do=thread&id=" + tNum;
					} },
				getTNum: { value: function value(op) {
						return $q("a[name]", op).name.match(/\d+/)[0];
					} },
				css: { value: ".reply { background-color: #e4e4d6; }" },
				init: { value: function value() {
						var el = $id("mainc"),
						    pArea = $id("postarea");
						$del(el.firstElementChild);
						$before(el, pArea.nextElementSibling);
						$before(el, pArea);
						return false;
					} },
				parseURL: { value: function value() {
						var url = window.location.search.match(/^\?do=(thread|page)&(id|p)=(\d+)$/);
						this.b = window.location.pathname.replace(/\//g, "");
						this.t = url[1] === "thread" ? url[3] : false;
						this.page = url[1] === "page" ? +url[3] : 0;
						this.docExt = "";
					} }
			}
		}, {
			"form[action$=\"board.php\"]": {
				get: function () {
					return this["script[src*=\"kusaba\"]"];
				},
				configurable: true,
				enumerable: true
			}
		});

		var ibBase = Object.defineProperties({
			cFileInfo: "filesize",
			cOPost: "oppost",
			cReply: "reply",
			cSubj: "filetitle",
			cTrip: "postertrip",
			qBan: "",
			qDelBut: "input[type=\"submit\"]",
			qDForm: "#delform, form[name=\"delform\"]",
			qError: "h1, h2, font[size=\"5\"]",
			qHide: ".de-post-btns ~ *",
			qMsg: "blockquote",
			qName: ".postername, .commentpostername",
			qOmitted: ".omittedposts",
			qPages: "table[border=\"1\"] > tbody > tr > td:nth-child(2) > a:last-of-type",
			qPostForm: "#postform",
			qPostRedir: "input[name=\"postredir\"][value=\"1\"]",
			qRef: ".reflink",
			qRPost: ".reply",
			qTable: "form > table, div > table, div[id^=\"repl\"]",
			qThumbImages: ".thumb, .de-thumb, .ca_thumb, img[src*=\"thumb\"], img[src*=\"/spoiler\"], img[src^=\"blob:\"]",
			qTrunc: ".abbrev, .abbr, .shortened",
			fixVideo: function fixVideo(post) {
				var videos = [],
				    els = $Q("embed, object, iframe", post ? post.el : dForm.el);
				for (var i = 0, len = els.length; i < len; ++i) {
					var m,
					    el = els[i],
					    src = el.src || el.data;
					if (src) {
						if (m = src.match(Videos.ytReg)) {
							videos.push([post || this.getPostEl(el).post, m, true]);
							$del(el);
						}
						if (Cfg.addVimeo && (m = src.match(Videos.vimReg))) {
							videos.push([post || this.getPostEl(el).post, m, false]);
							$del(el);
						}
					}
				}
				return videos;
			},
			getFileInfo: function getFileInfo(wrap) {
				var el = $c(this.cFileInfo, wrap);
				return el ? el.textContent : "";
			},
			getImgLink: function getImgLink(img) {
				var el = img.parentNode;
				return el.tagName === "SPAN" ? el.parentNode : el;
			},
			getImgParent: function getImgParent(el) {
				return this.getImgWrap(el);
			},
			getImgWrap: function getImgWrap(el) {
				var node = (el.tagName === "SPAN" ? el.parentNode : el).parentNode;
				return node.tagName === "SPAN" ? node.parentNode : node;
			},
			getOmitted: function getOmitted(el, len) {
				var txt;
				return el && (txt = el.textContent) ? +(txt.match(/\d+/) || [0])[0] + 1 : 1;
			},
			getOp: function getOp(thr) {
				var op = localRun ? $q("div[de-oppost]", thr) : $c(this.cOPost, thr);
				if (op) {
					return op;
				}
				op = thr.ownerDocument.createElement("div");
				op.setAttribute("de-oppost", "");
				var el,
				    opEnd = $q(this.qTable, thr);
				while ((el = thr.firstChild) !== opEnd) {
					op.appendChild(el);
				}
				if (thr.hasChildNodes()) {
					thr.insertBefore(op, thr.firstChild);
				} else {
					thr.appendChild(op);
				}
				return op;
			},
			getPNum: function getPNum(post) {
				return post.id.match(/\d+/)[0];
			},
			getPageUrl: function getPageUrl(b, p) {
				return fixBrd(b) + (p > 0 ? p + this.docExt : "");
			},
			getPostEl: function getPostEl(el) {
				var sel = this.qRPost + ", [de-thread]";
				while (el && !nav.matchesSelector(el, sel)) {
					el = el.parentElement;
				}
				return el;
			},
			getSage: function getSage(post) {
				var a = $q("a[href^=\"mailto:\"], a[href=\"sage\"]", post);
				return !!a && /sage/i.test(a.href);
			},
			getThrdUrl: function getThrdUrl(b, tNum) {
				return this.prot + "//" + this.host + fixBrd(b) + this.res + tNum + this.docExt;
			},
			getTNum: function getTNum(op) {
				return $q("input[type=\"checkbox\"]", op).value;
			},
			getWrap: function getWrap(el, isOp) {
				if (isOp) {
					return el;
				}
				if (el.tagName === "TD") {
					Object.defineProperty(this, "getWrap", { value: function value(el, isOp) {
							return isOp ? el : $parent(el, "TABLE");
						} });
				} else {
					Object.defineProperty(this, "getWrap", { value: function value(el, isOp) {
							return el;
						} });
				}
				return this.getWrap(el, isOp);
			},
			insertYtPlayer: function insertYtPlayer(msg, playerHtml) {
				msg.insertAdjacentHTML("beforebegin", playerHtml);
				return msg.previousSibling;
			},
			anchor: "#",
			css: "",
			cssEn: "",
			disableRedirection: function disableRedirection(el) {
				if (this.qPostRedir) {
					($q(this.qPostRedir, el) || {}).checked = true;
				}
			},
			dm: "",
			docExt: ".html",
			LastModified: null,
			earlyInit: null,
			ETag: null,
			firstPage: 0,
			fixFileInputs: emptyFn,
			hasPicWrap: false,
			host: window.location.hostname,
			init: null,
			markupBB: false,
			multiFile: false,
			parseURL: function parseURL() {
				var url = (window.location.pathname || "").match(new RegExp("^(?:\\/?([^\\.]*?(?:\\/[^\\/]*?)?)\\/?)?" + "(" + regQuote(this.res) + ")?" + "(\\d+|index|wakaba|futaba)?" + "(\\.(?:[a-z]+))?(?:\\/|$)"));
				this.b = url[1].replace(/\/$/, "");
				this.t = url[2] ? url[3] : this.futa ? +(window.location.search.match(/\d+/) || [false])[0] : false;
				this.page = url[3] && !this.t ? +url[3] || this.firstPage : this.firstPage;
				if (!this.hasOwnProperty("docExt") && url[4]) {
					this.docExt = url[4];
				}
			},
			prot: prot,
			res: "res/",
			rLinkClick: "onclick=\"highlight(this.textContent.substr(2))\"",
			ru: false,
			timePattern: "w+dd+m+yyyy+hh+ii+ss",
			thrid: "parent"
		}, {
			qImgLink: {
				get: function () {
					var val = "." + this.cFileInfo + " a[href$=\".jpg\"], " + "." + this.cFileInfo + " a[href$=\".jpeg\"], " + "." + this.cFileInfo + " a[href$=\".png\"], " + "." + this.cFileInfo + " a[href$=\".gif\"], " + "." + this.cFileInfo + " a[href$=\".webm\"]";
					Object.defineProperty(this, "qImgLink", { value: val });
					return val;
				},
				configurable: true,
				enumerable: true
			},
			qMsgImgLink: {
				get: function () {
					var val = this.qMsg + " a[href*=\".jpg\"], " + this.qMsg + " a[href*=\".png\"], " + this.qMsg + " a[href*=\".gif\"], " + this.qMsg + " a[href*=\".jpeg\"]";
					Object.defineProperty(this, "qMsgImgLink", { value: val });
					return val;
				},
				configurable: true,
				enumerable: true
			},
			qThread: {
				get: function () {
					var val = $c("thread", doc) ? ".thread" : $q("div[id*=\"_info\"][style*=\"float\"]", doc) ? "div[id^=\"t\"]:not([style])" : "[id^=\"thread\"]";
					Object.defineProperty(this, "qThread", { value: val });
					return val;
				},
				configurable: true,
				enumerable: true
			},
			lastPage: {
				get: function () {
					var el = $q(this.qPages, doc),
					    val = el && +aProto.pop.call(el.textContent.match(/\d+/g) || []) || 0;
					if (this.page === val + 1) {
						val++;
					}
					Object.defineProperty(this, "lastPage", { value: val });
					return val;
				},
				configurable: true,
				enumerable: true
			},
			markupTags: {
				get: function () {
					return this.markupBB ? ["b", "i", "u", "s", "spoiler", "code", "", "", "q"] : ["**", "*", "", "^H", "%%", "`", "", "", "q"];
				},
				configurable: true,
				enumerable: true
			},
			reCrossLinks: {
				get: function () {
					var val = new RegExp(">https?:\\/\\/[^\\/]*" + this.dm + "\\/([a-z0-9]+)\\/" + regQuote(this.res) + "(\\d+)(?:[^#<]+)?(?:#i?(\\d+))?<", "g");
					Object.defineProperty(this, "reCrossLinks", { value: val });
					return val;
				},
				configurable: true,
				enumerable: true
			},
			rep: {
				get: function () {
					var val = dTime || spells.haveReps || Cfg.crossLinks;
					Object.defineProperty(this, "rep", { value: val });
					return val;
				},
				configurable: true,
				enumerable: true
			}
		});

		localRun = prot === "file:";
		var ibObj = null,
		    dm = localRun ? (window.location.pathname.match(/\/([^-]+)-[^-]+-[^\.]+\.[a-z]+$/) || [, ""])[1] : window.location.hostname.match(/(?:(?:[^.]+\.)(?=org\.|net\.|com\.))?[^.]+\.[^.]+$|^\d+\.\d+\.\d+\.\d+$|localhost/)[0];
		if (checkDomains) {
			if (dm in ibDomains) {
				ibObj = (function createBoard(info) {
					return Object.create(info[2] ? createBoard(ibDomains[info[2]]) : info[1] ? Object.create(ibBase, ibEngines[info[1]]) : ibBase, info[0]);
				})(ibDomains[dm]);
				checkEngines = false;
			}
		}
		if (checkEngines) {
			for (var i in ibEngines) {
				if ($q(i, doc)) {
					ibObj = Object.create(ibBase, ibEngines[i]);
					break;
				}
			}
			if (!ibObj) {
				ibObj = ibBase;
			}
		}
		if (ibObj) {
			ibObj.dm = dm;
		}
		return ibObj;
	}




	function Initialization(checkDomains) {
		if (!aib) {
			aib = getImageBoard(checkDomains, true);
		}
		if (checkDomains && aib.earlyInit) {
			if (!checkStorage()) {
				return null;
			}
			if (aib.earlyInit()) {
				window.location.reload();
				return null;
			}
		}
		if (aib.init && aib.init() || $id("de-panel")) {
			return null;
		}
		var formEl = $q(aib.qDForm + ", form[de-form]", doc);
		if (!formEl) {
			return null;
		}
		if (!locStorage && !checkStorage()) {
			return null;
		}
		if (!nav) {
			initNavFuncs();
		}
		doc.defaultView.addEventListener("storage", function (e) {
			var data,
			    temp,
			    post,
			    val = e.newValue;
			if (!val) {
				return;
			}
			switch (e.key) {
				case "__de-post":
					(function () {
						try {
							data = JSON.parse(val);
						} catch (err) {
							return;
						}
						temp = data.hide;
						if (data.brd === aib.b && (post = pByNum[data.num]) && post.hidden ^ temp) {
							post.setUserVisib(temp, data.date, false);
						} else {
							uVis[data.num] = [+!temp, data.date];
						}
						if (data.isOp) {
							if (!(data.brd in hThr)) {
								if (temp) {
									hThr[data.brd] = {};
								} else {
									toggleContent("hid", true);
									return;
								}
							}
							if (temp) {
								hThr[data.brd][data.num] = data.title;
							} else {
								delete hThr[data.brd][data.num];
							}
						}
						toggleContent("hid", true);
					})();
					return;
				case "__de-threads":
					(function () {
						try {
							hThr = JSON.parse(val);
						} catch (err) {
							return;
						}
						if (!(aib.b in hThr)) {
							hThr[aib.b] = {};
						}
						dForm.firstThr.updateHidden(hThr[aib.b]);
						toggleContent("hid", true);
					})();
					return;
				case "__de-spells":
					(function () {
						try {
							data = JSON.parse(val);
						} catch (err) {
							return;
						}
						Cfg.hideBySpell = data.hide;
						temp = $q("input[info=\"hideBySpell\"]", doc);
						if (temp) {
							temp.checked = data.hide;
						}
						doc.body.style.display = "none";
						disableSpells();
						if (data.data) {
							spells.setSpells(data.data, false);
							temp = $id("de-spell-edit");
							if (temp) {
								temp.value = spells.list;
							}
						} else {
							if (data.data === "") {
								spells.disable();
								temp = $id("de-spell-edit");
								if (temp) {
									temp.value = "";
								}
								saveCfg("spells", "");
							}
							spells.enable = false;
						}
						doc.body.style.display = "";
					})();
								default:
					return;
			}
		}, false);

		if (localRun) {
			var url = window.location.pathname.match(/\/[^-]+-([^-]+)-([^\.]+)\.[a-z]+$/);
			aib.prot = "http:";
			aib.host = aib.dm;
			aib.b = url ? url[1] : "";
			aib.t = url ? url[2] : "";
			aib.page = 0;
			aib.docExt = ".html";
		} else {
			aib.parseURL();
		}
		if (aib.t) {
			doc.defaultView.addEventListener("beforeunload", function (e) {
				sesStorage["de-scroll-" + aib.b + aib.t] = window.pageYOffset;
			}, false);
		}
		dummy = doc.createElement("div");
		return formEl;
	}




	function DelForm(formEl, isLight) {
		this.el = formEl;
		this.isLight = isLight;
		this.tNums = [];
		this.addFormContent(formEl, false);
		formEl.setAttribute("de-form", "");
		formEl.removeAttribute("id");
	}
	DelForm.doReplace = function (formEl) {
		if (aib.rep) {
			formEl.insertAdjacentHTML("beforebegin", replaceString(formEl.outerHTML));
			formEl.style.display = "none";
			formEl.id = "de-dform-old";
			formEl = formEl.previousSibling;
			window.addEventListener("load", function () {
				$del($id("de-dform-old"));
			}, false);
		}
		return formEl;
	};
	DelForm.prototype = {
		el: null,
		firstThr: null,
		isLight: false,
		lastThr: null,
		tNums: null,
		addFormContent: function addFormContent(formEl, append) {
			var threads = $Q(aib.qThread, formEl),
			    len = threads.length;
			$each($T("script", formEl), $del);
			if (len === 0) {
				if (localRun) {
					threads = $Q("div[de-thread]", doc);
					len = threads.length;
				}
				if (len === 0) {
					threads = this._parseThreads(formEl);
					len = threads.length;
				}
			}
			if (len === 0) {
				return;
			}
			if (append) {
				while (formEl.firstChild) {
					this.el.appendChild(formEl.firstChild);
				}
			}
			var thr = new Thread(threads[0], this.lastThr, this.isLight);
			if (this.firstThr === null) {
				this.firstThr = thr;
			}
			for (var i = 1; i < len; ++i) {
				this.tNums.push(+thr.num);
				thr = new Thread(threads[i], thr, this.isLight);
			}
			this.tNums.push(+thr.num);
			this.lastThr = thr;
		},
		clear: function clear() {
			$each($Q("a[href^=\"blob:\"]", this.el), function (a) {
				window.URL.revokeObjectURL(a.href);
			});
			this.firstThr = this.lastThr = null;
			this.tNums = [];
			this.el.innerHTML = "";
		},
		hide: function hide() {
			this.el.style.display = "none";
		},
		initAjax: function initAjax() {
			var _this = this;

			if (Cfg.ajaxReply === 2) {
				this.el.onsubmit = $pd;
				var btn = $q(aib.qDelBut, this.el);
				if (btn) {
					btn.onclick = function (e) {
						$pd(e);
						pr.closeQReply();
						$alert(Lng.deleting[lang], "delete", true);
						spawn(html5Submit, _this.el).then(checkDelete, function (e) {
							return $alert(getErrorMessage(e), "delete", false);
						});
					};
				}
			} else if (Cfg.ajaxReply === 1) {
				this.el.insertAdjacentHTML("beforeend", "<iframe name=\"de-iframe-pform\" src=\"about:blank\" style=\"display: none;\"></iframe>" + "<iframe name=\"de-iframe-dform\" src=\"about:blank\" style=\"display: none;\"></iframe>");
				this.el.target = "de-iframe-dform";
				this.el.onsubmit = function () {
					pr.closeQReply();
					$alert(Lng.deleting[lang], "deleting", true);
				};
			}
		},
		show: function show() {
			this.el.style.display = "";
		},

		_parseThreads: function _parseThreads(formEl) {
			var i,
			    len,
			    threads = [],
			    fNodes = aProto.slice.call(formEl.childNodes),
			    cThr = doc.createElement("div");
			for (i = 0, len = fNodes.length - 1; i < len; ++i) {
				var node = fNodes[i];
				if (node.tagName === "HR") {
					formEl.insertBefore(cThr, node);
					formEl.insertBefore(cThr.lastElementChild, node);
					var el = cThr.lastElementChild;
					if (el.tagName === "BR") {
						formEl.insertBefore(el, node);
					}
					threads.push(cThr);
					cThr = doc.createElement("div");
				} else {
					cThr.appendChild(node);
				}
			}
			cThr.appendChild(fNodes[i]);
			formEl.appendChild(cThr);
			return threads;
		}
	};

	function replaceString(txt) {
		if (dTime) {
			txt = dTime.fix(txt);
		}
		if (aib.fch || aib.krau) {
			if (aib.fch) {
				txt = txt.replace(/<\/?wbr>/g, "").replace(/ \(OP\)<\/a/g, "</a");
			}
			if (aib.krau) {
				txt = txt.replace(/href="(#\d+)"/g, "href=\"/" + aib.b + "/thread-" + aib.t + ".html$1\"").replace(/<span class="invalidquotelink">&gt;&gt;(\d+)<\/span>/g, "<a class=\"de-ref-del\" href=\"#$1\" onclick=\"highlightPost($1)\">&gt;&gt;$1</a>");
			}
			txt = txt.replace(/(^|>|\s|&gt;)(https*:\/\/[^"<>]*?)(<\/a>)?(?=$|<|\s)/ig, function (x, a, b, c) {
				return c ? x : a + "<a href=\"" + b + "\">" + b + "</a>";
			});
		}
		if (spells.haveReps) {
			txt = spells.replace(txt);
		}
		if (Cfg.crossLinks) {
			txt = txt.replace(aib.reCrossLinks, function (str, b, tNum, pNum) {
				return ">&gt;&gt;/" + b + "/" + (pNum || tNum) + "<";
			});
		}
		return txt;
	}

	function replacePost(el) {
		if (aib.rep) {
			el.innerHTML = replaceString(el.innerHTML);
		}
		return el;
	}

	function initThreadUpdater(title, enableUpdate) {
		var focused,
		    startLoad,
		    audioRep,
		    audioEl,
		    stateButton,
		    hasAudio,
		    initDelay,
		    favIntrv,
		    favNorm,
		    favHref,
		    notifGranted,
		    countEl,
		    useCountdown,
		    canFocusLoad,
		    paused,
		    enabled = false,
		    stopLoad = emptyFn,
		    disabledByUser = true,
		    inited = false,
		    lastECode = 200,
		    sendError = false,
		    newPosts = 0,
		    aPlayers = 0;

		function init() {
			audioEl = null;
			stateButton = null;
			hasAudio = false;
			initDelay = Cfg.updThrDelay * 1000;
			favIntrv = 0;
			favNorm = notifGranted = inited = true;
			favHref = ($q("head link[rel=\"shortcut icon\"]", doc) || {}).href;
			useCountdown = !!Cfg.updCount;
			enable();
			startLoad(true);
		}

		function enable() {
			enabled = canFocusLoad = true;
			paused = disabledByUser = false;
			newPosts = 0;
			if (useCountdown) {
				countEl = $id("de-updater-count");
				countEl.style.display = "";
			}
		}

		function disable(byUser) {
			disabledByUser = byUser;
			if (enabled) {
				stopLoad(true);
				enabled = hasAudio = false;
				setState("off");
				var btn = $id("de-btn-audio-on");
				if (btn) {
					btn.id = "de-btn-audio-off";
				}
			}
		}

		function audioNotif() {
			if (focused) {
				hasAudio = false;
			} else {
				audioEl.play();
				setTimeout(audioNotif, audioRep);
				hasAudio = true;
			}
		}

		function requestNotifPermission() {
			notifGranted = false;
			Notification.requestPermission(function (state) {
				if (state.toLowerCase() === "denied") {
					saveCfg("desktNotif", 0);
				} else {
					notifGranted = true;
				}
			});
		}

		function checkFocusLoad(isFocusLoad) {
			if (isFocusLoad) {
				if (!canFocusLoad) {
					return false;
				}
				canFocusLoad = false;
				setTimeout(function () {
					canFocusLoad = true;
				}, 10000);
			}
			return true;
		}

		function forceLoadPosts(isFocusLoad) {
			if (paused) {
				return;
			}
			if (!enabled && !disabledByUser) {
				enable();
			}
			if (!checkFocusLoad(isFocusLoad)) {
				return;
			}
			stopLoad(false);
			startLoad(false);
		}

		function favIcoBlink() {
			$del($q("link[rel=\"shortcut icon\"]", doc.head));
			doc.head.insertAdjacentHTML("afterbegin", "<link rel=\"shortcut icon\" href=\"" + (!favNorm ? favHref : "data:image/x-icon;base64," + this) + "\">");
			favNorm = !favNorm;
		}

		function StopLoadingTaskError() {
			this.name = "StopLoadingTaskError";
		}

		function stopLoadHelper(reject, hideCountdown) {
			reject(new StopLoadingTaskError());
			if (useCountdown) {
				if (hideCountdown) {
					countEl.style.display = "none";
				} else {
					countEl.innerHTML = "<span class=\"de-wait\"></span>";
				}
			}
			stopLoad = emptyFn;
		}

		startLoad = async(regeneratorRuntime.mark(function callee$2$0(needSleep) {
			var delay, repeatLoading, stopToken, seconds, error, lPosts, eCode, post, notif;
			return regeneratorRuntime.wrap(function callee$2$0$(context$3$0) {
				while (1) switch (context$3$0.prev = context$3$0.next) {
					case 0:
						delay = initDelay, repeatLoading = enabled, stopToken = new Promise(function (resolve, reject) {
							return stopLoad = stopLoadHelper.bind(null, reject);
						});

					case 1:
						if (!needSleep) {
							context$3$0.next = 24;
							break;
						}

						context$3$0.prev = 2;

						if (!(useCountdown && (focused || !canFocusLoad))) {
							context$3$0.next = 14;
							break;
						}

						seconds = delay / 1000;

					case 5:
						if (!(seconds > 0)) {
							context$3$0.next = 12;
							break;
						}

						countEl.textContent = seconds;
						context$3$0.next = 9;
						return Promise.race([stopToken, sleep(1000)]);

					case 9:
						--seconds;
						context$3$0.next = 5;
						break;

					case 12:
						context$3$0.next = 16;
						break;

					case 14:
						context$3$0.next = 16;
						return Promise.race([stopToken, sleep(delay)]);

					case 16:
						context$3$0.next = 22;
						break;

					case 18:
						context$3$0.prev = 18;
						context$3$0.t28 = context$3$0["catch"](2);

						if (!(context$3$0.t28 instanceof StopLoadingTaskError)) {
							context$3$0.next = 22;
							break;
						}

						return context$3$0.abrupt("return");

					case 22:
						context$3$0.next = 25;
						break;

					case 24:
						needSleep = true;

					case 25:
						if (useCountdown) {
							countEl.innerHTML = "<span class=\"de-wait\"></span>";
						}
						error = AjaxError.Success, lPosts = 0;
						context$3$0.prev = 27;
						context$3$0.next = 30;
						return Promise.race([stopToken, dForm.firstThr.loadNew(true)]);

					case 30:
						lPosts = context$3$0.sent;
						context$3$0.next = 38;
						break;

					case 33:
						context$3$0.prev = 33;
						context$3$0.t29 = context$3$0["catch"](27);

						if (!(context$3$0.t29 instanceof StopLoadingTaskError)) {
							context$3$0.next = 37;
							break;
						}

						return context$3$0.abrupt("return");

					case 37:
						error = context$3$0.t29;

					case 38:
						infoLoadErrors(error, false);
						eCode = error instanceof AjaxError ? error.code : 0;

						if (!(eCode !== 200 && eCode !== 304)) {
							context$3$0.next = 52;
							break;
						}

						if (Cfg.favIcoBlink && !focused && favHref) {
							clearInterval(favIntrv);
							favIntrv = setInterval(favIcoBlink.bind("iVBORw0KGgoAAAANSUhEUgAAABAAAAAQBAMAAADt3eJSAAAAA3NCSVQICAjb4U/gAAAALVBMVEX////QRDfQRDfQRDfQRDfQRDfQRDfQRDfQRDfQRDfQRDfQRDfQRDfQRDfQRDdiAd5MAAAAD3RSTlMAESIzRFVmd4iZu8zd7v9ufV8LAAAACXBIWXMAAAsSAAALEgHS3X78AAAAFXRFWHRDcmVhdGlvbiBUaW1lADEwLzIvMTOFMzGTAAAAHHRFWHRTb2Z0d2FyZQBBZG9iZSBGaXJld29ya3MgQ1M26LyyjAAAAH9JREFUCJljYEAAjbO3C0E067l37946ABlxLxWY6q4wMDDde+PAwPxGgYHj5bnLDAx1BQw8j3yBKvQ2MPA9YL53mIHvAJDB4PPOAMjgfsTA/O4wUIrjOQODzdt5CQyM9wwYmO+9EWBg8H2uwDTvMdBkFqAVbwxAlqmvOV2I5AYASFUrcXUe0gcAAAAASUVORK5CYII="), 800);
						}

						if (!(eCode === 404 && lastECode === 404)) {
							context$3$0.next = 46;
							break;
						}

						updateTitle(eCode);
						disable(false);
						return context$3$0.abrupt("return");

					case 46:
						lastECode = eCode;
						setState("warn");
						if (!Cfg.noErrInTitle) {
							updateTitle();
						}
						return context$3$0.abrupt("continue", 55);

					case 52:
						if (lastECode !== 200) {
							restoreFavicon();
							setState("on");
							if (!Cfg.noErrInTitle) {
								updateTitle(eCode);
							}
						}

					case 53:
						lastECode = eCode;
						if (!focused) {
							if (lPosts !== 0) {
								if (Cfg.favIcoBlink && favHref && newPosts === 0) {
									favIntrv = setInterval(favIcoBlink.bind("iVBORw0KGgoAAAANSUhEUgAAABAAAAAQEAYAAABPYyMiAAAABmJLR0T///////8JWPfcAAAACXBIWXMAAABIAAAASABGyWs+AAAAF0lEQVRIx2NgGAWjYBSMglEwCkbBSAcACBAAAeaR9cIAAAAASUVORK5CYII="), 800);
								}
								newPosts += lPosts;
								updateTitle();
								if (Cfg.desktNotif && notifGranted) {
									post = dForm.firstThr.last, notif = new Notification(aib.dm + "/" + aib.b + "/" + aib.t + ": " + newPosts + Lng.newPost[lang][lang !== 0 ? +(newPosts !== 1) : newPosts % 10 > 4 || newPosts % 10 === 0 || (newPosts % 100 / 10 | 0) === 1 ? 2 : newPosts % 10 === 1 ? 0 : 1] + Lng.newPost[lang][3], {
										body: post.text.substring(0, 250).replace(/\s+/g, " "),
										tag: aib.dm + aib.b + aib.t,
										icon: post.images.firstAttach ? post.images.firstAttach.src : favHref
									});

									notif.onshow = setTimeout.bind(window, notif.close.bind(notif), 12000);
									notif.onclick = window.focus;
									notif.onerror = function () {
										window.focus();
										requestNotifPermission();
									};
								}
								if (hasAudio) {
									if (audioRep) {
										audioNotif();
									} else {
										audioEl.play();
									}
								}
								delay = initDelay;
							} else if (delay !== 120000) {
								delay = Math.min(delay + initDelay, 120000);
							}
						}

					case 55:
						if (repeatLoading) {
							context$3$0.next = 1;
							break;
						}

					case 56:
						stopLoad = emptyFn;

					case 57:
					case "end":
						return context$3$0.stop();
				}
			}, callee$2$0, this, [[2, 18], [27, 33]]);
		}));

		function setState(state) {
			var btn = stateButton || (stateButton = $q("a[id^=\"de-btn-upd\"]", doc));
			btn.id = "de-btn-upd-" + state;
			btn.title = Lng.panelBtn["upd-" + (state === "off" ? "off" : "on")][lang];
		}

		function restoreFavicon() {
			if (Cfg.favIcoBlink && favHref) {
				clearInterval(favIntrv);
				favNorm = true;
				$del($q("link[rel=\"shortcut icon\"]", doc.head));
				doc.head.insertAdjacentHTML("afterbegin", "<link rel=\"shortcut icon\" href=\"" + favHref + "\">");
			}
		}

		function onVis() {
			restoreFavicon();
			newPosts = 0;
			focused = true;
			sendError = false;
			setTimeout(function () {
				updateTitle();
				if (enabled) {
					forceLoadPosts(true);
				}
			}, 200);
		}

		function updateTitle() {
			var eCode = arguments[0] === undefined ? lastECode : arguments[0];

			doc.title = (aPlayers === 0 ? "" : "♫ ") + (sendError === true ? "{" + Lng.error[lang] + "} " : "") + (eCode === 200 ? "" : "{" + eCode + "} ") + (newPosts === 0 ? "" : "[" + newPosts + "] ") + title;
		}

		if ("hidden" in doc) {
			focused = !doc.hidden;
			doc.addEventListener("visibilitychange", function () {
				if (doc.hidden) {
					focused = false;
					if (dForm.firstThr) {
						dForm.firstThr.clearPostsMarks();
					}
				} else {
					onVis();
				}
			}, false);
		} else {
			focused = false;
			doc.defaultView.addEventListener("focus", onVis, false);
			doc.defaultView.addEventListener("blur", function () {
				focused = false;
				dForm.firstThr.clearPostsMarks();
			}, false);
			doc.defaultView.addEventListener("mousemove", function mouseMove() {
				doc.defaultView.removeEventListener("mousemove", mouseMove, false);
				onVis();
			}, false);
		}
		if (enableUpdate) {
			init();
		}
		if (focused && Cfg.desktNotif && "permission" in Notification) {
			switch (Notification.permission.toLowerCase()) {
				case "default":
					requestNotifPermission();break;
				case "denied":
					saveCfg("desktNotif", 0);
			}
		}

		return Object.defineProperties({
			forceLoad: function forceLoad(e) {
				if (e) {
					$pd(e);
				}
				dForm.firstThr.clearPostsMarks();
				if (paused) {
					return;
				}
				$alert(Lng.loading[lang], "newposts", true);
				forceLoadPosts(false);
			},
			enable: (function (_enable) {
				var _enableWrapper = function enable() {
					return _enable.apply(this, arguments);
				};

				_enableWrapper.toString = function () {
					return _enable.toString();
				};

				return _enableWrapper;
			})(function () {
				if (!inited) {
					init();
				} else if (!enabled) {
					enable();
					startLoad(true);
				} else {
					return;
				}
				setState("on");
			}),
			pause: function pause() {
				if (enabled && !paused) {
					stopLoad(false);
					paused = true;
				}
			},
			"continue": function () {
				if (enabled && paused) {
					startLoad(false);
					paused = false;
				}
			},
			disable: disable.bind(null, true),
			toggleAudio: function toggleAudio(aRep) {
				if (!audioEl) {
					audioEl = $new("audio", {
						preload: "auto",
						src: "https://raw.github.com/SthephanShinkufag/Dollchan-Extension-Tools/master/signal.ogg"
					}, null);
				}
				audioRep = aRep;
				return hasAudio = !hasAudio;
			},
			toggleCounter: function toggleCounter(enableCnt) {
				if (enableCnt) {
					if (!countEl) {
						countEl = $id("de-updater-count");
					}
					countEl.innerHTML = "<span class=\"de-wait\"></span>";
					countEl.style.display = "";
					useCountdown = true;
					forceLoadPosts(false);
				} else {
					countEl.style.display = "none";
					useCountdown = false;
				}
			},
			addPlayingTag: function addPlayingTag() {
				aPlayers++;
				if (aPlayers === 1) {
					updateTitle();
				}
			},
			removePlayingTag: function removePlayingTag() {
				aPlayers = Math.max(aPlayers - 1, 0);
				if (aPlayers === 0) {
					updateTitle();
				}
			},
			sendErrNotif: function sendErrNotif() {
				if (Cfg.sendErrNotif && !focused) {
					sendError = true;
					updateTitle();
				}
			}
		}, {
			enabled: {
				get: function () {
					return enabled;
				},
				configurable: true,
				enumerable: true
			},
			focused: {
				get: function () {
					return focused;
				},
				configurable: true,
				enumerable: true
			}
		});
	}

	function initPage() {
		if (aib.t) {
			if (Cfg.rePageTitle) {
				doc.title = "/" + aib.b + " - " + dForm.firstThr.op.title;
			}
			if (!localRun) {
				dForm.firstThr.el.insertAdjacentHTML("afterend", "<div id=\"de-updater-div\">&gt;&gt; [<a class=\"de-abtn\" id=\"de-updater-btn\" href=\"#\"></a>" + "<span id=\"de-updater-count\" style=\"display: none;\"></span>]" + (aib.mak ? "[<a class=\"de-abtn\" href=\"#\" onclick=\"UnbanShow();\">Реквест разбана</a>]" : "") + "</div>");
			}
		}
		if (!localRun) {
			updater = initThreadUpdater(doc.title, aib.t && Cfg.ajaxUpdThr);
			if (aib.t) {
				dForm.firstThr.el.nextSibling.firstElementChild.addEventListener("click", updater.forceLoad, false);
			}
		}
	}

	function scrollPage() {
		if (!aib.t) {
			if (!updater.focused || window.pageYOffset !== 0) {
				window.scrollTo(0, 0);
			}
			return;
		}
		setTimeout(function () {
			var post,
			    num,
			    hash,
			    val = +sesStorage["de-scroll-" + aib.b + aib.t];
			if (val) {
				window.scrollTo(0, val);
				sesStorage.removeItem("de-scroll-" + aib.b + aib.t);
			} else if ((hash = window.location.hash) && (num = hash.match(/#i?(\d+)$/)) && (num = num[1]) && (post = pByNum[num])) {
				post.el.scrollIntoView(true);
			}
		}, 0);
	}

	function checkForUpdates(isForce, lastUpdateTime) {
		if (!isForce) {
			var day = 2 * 1000 * 60 * 60 * 24,
			    temp = Cfg.scrUpdIntrv;
			switch (temp) {
				case 0:
					temp = day;break;
				case 1:
					temp = day * 2;break;
				case 2:
					temp = day * 7;break;
				case 3:
					temp = day * 14;break;
				default:
					temp = day * 30;
			}
			if (Date.now() - +lastUpdateTime < temp) {
				return Promise.reject();
			}
		}
		return $ajax("https://raw.github.com/SthephanShinkufag/Dollchan-Extension-Tools/master/Dollchan_Extension_Tools.meta.js", { "Content-Type": "text/plain" }, false).then(function (xhr) {
			var m = xhr.responseText.match(/@version\s+([0-9.]+)/),
			    dVer = m && m[1] ? m[1].split(".") : null;
			if (dVer) {
				var cVer = version.split(".");
				saveComCfg("lastUpd", Date.now());
				for (var i = 0, len = Math.max(cVer.length, dVer.length); i < len; ++i) {
					if ((+dVer[i] || 0) > (+cVer[i] || 0)) {
						return "<a style=\"color: blue; font-weight: bold;\" href=\"" + "https://raw.github.com/SthephanShinkufag/Dollchan-Extension-Tools/master/" + "Dollchan_Extension_Tools.user.js\">" + Lng.updAvail[lang] + "</a>";
					} else if ((+dVer[i] || 0) < (+cVer[i] || 0)) {
						break;
					}
				}
				if (isForce) {
					return Lng.haveLatest[lang];
				}
			}
			return Promise.reject();
		}, function () {
			return isForce ? "<div style=\"color: red; font-weigth: bold;\">" + Lng.noConnect[lang] + "</div>" : Promise.reject();
		});
	}




	function getThemeLang() {
		return !Cfg.scriptStyle ? "fr" : Cfg.scriptStyle === 1 ? "en" : "de";
	}

	function scriptCSS() {
		function cont(id, src) {
			return id + ":before { content: \"\"; padding: 0 16px 0 0; margin: 0 4px; background: url(" + src + ") no-repeat center; }";
		}
		function gif(id, src) {
			return id + " { background: url(data:image/gif;base64," + src + ") no-repeat center !important; }";
		}

	
		var p,
		    x = "\t.de-block { display: block; }\t#de-content-cfg > div { float: left; border-radius: 10px 10px 0 0; width: auto; min-width: 0; padding: 0; margin: 5px 20px; border: none; }\t#de-content-cfg input[type=\"button\"] { padding: 0 2px; margin: 0 1px; height: 24px; }\t.de-cfg-head { padding: 2px; border-radius: 10px 10px 0 0; color: #fff; text-align: center; font: bold 14px arial; cursor: default; }\t.de-cfg-head:lang(en), #de-panel:lang(en) { background: linear-gradient(to bottom, #4b90df, #3d77be 5px, #376cb0 7px, #295591 13px, rgba(0,0,0,0) 13px), linear-gradient(to bottom, rgba(0,0,0,0) 12px, #183d77 13px, #1f4485 18px, #264c90 20px, #325f9e 25px); }\t.de-cfg-head:lang(fr), #de-panel:lang(fr) { background: linear-gradient(to bottom, #7b849b, #616b86 2px, #3a414f 13px, rgba(0,0,0,0) 13px), linear-gradient(to bottom, rgba(0,0,0,0) 12px, #121212 13px, #1f2740 25px); }\t.de-cfg-head:lang(de), #de-panel:lang(de) { background: #777; }\t.de-cfg-body { min-height: 309px; width: 354px; padding: 11px 7px 7px; margin-top: -1px; font: 13px sans-serif !important; box-sizing: content-box; -moz-box-sizing: content-box; }\t.de-cfg-body input[type=\"text\"], .de-cfg-body select { width: auto; padding: 1px 2px; margin: 1px 0; font: 13px sans-serif; }\t.de-cfg-body input[type=\"checkbox\"] { " + (nav.Presto ? "" : "vertical-align: -1px; ") + "margin: 2px 1px; }\t.de-cfg-body label { padding: 0; margin: 0; }\t.de-cfg-body, #de-cfg-btns { border: 1px solid #183d77; border-top: none; }\t.de-cfg-body:lang(de), #de-cfg-btns:lang(de) { border-color: #444; }\t#de-cfg-btns { padding: 3px; font-size: 13px; }\t#de-cfg-bar { width: 100%; display: table; background-color: #1f2740; margin: 0; padding: 0; }\t#de-cfg-bar:lang(en) { background-color: #325f9e; }\t#de-cfg-bar:lang(de) { background-color: #777; }\t.de-cfg-depend { padding-left: 17px; }\t.de-cfg-info-data { display: inline-block; padding: 0 7px; width: 162px; height: 238px; overflow-y: auto; border-collapse: separate; border-spacing: 1px; box-sizing: content-box; -moz-box-sizing: content-box; }\t.de-cfg-info-data > tbody > tr > td:first-child { width: 100%; }\t.de-cfg-info-data > tbody > tr > td:last-child { text-align: right; }\t.de-cfg-tab { padding: 4px 3px; border-radius: 4px 4px 0 0; font: bold 12px arial; text-align: center; cursor: default; }\t.de-cfg-tab-back { display: table-cell !important; float: none !important; width: auto !important; min-width: 0 !important; padding: 0 !important; box-shadow: none !important; border: 1px solid #183d77 !important; border-radius: 4px 4px 0 0 !important; opacity: 1; }\t.de-cfg-tab-back:lang(de) { border-color: #444 !important; }\t.de-cfg-tab-back:lang(fr) { border-color: #121421 !important; }\t.de-cfg-tab-back[selected=\"true\"] { border-bottom: none !important; }\t.de-cfg-tab-back[selected=\"false\"] > .de-cfg-tab { background-color: rgba(0,0,0,.2); }\t.de-cfg-tab-back[selected=\"false\"] > .de-cfg-tab:lang(en), .de-cfg-tab-back[selected=\"false\"] > .de-cfg-tab:lang(fr) { background: linear-gradient(to bottom, rgba(132,132,132,.35) 0%, rgba(79,79,79,.35) 50%, rgba(40,40,40,.35) 50%, rgba(80,80,80,.35) 100%) !important; }\t.de-cfg-tab-back[selected=\"false\"] > .de-cfg-tab:hover { background-color: rgba(99,99,99,.2); }\t.de-cfg-tab-back[selected=\"false\"] > .de-cfg-tab:hover:lang(en), .de-cfg-tab-back[selected=\"false\"] > .de-cfg-tab:hover:lang(fr)  { background: linear-gradient(to top, rgba(132,132,132,.35) 0%, rgba(79,79,79,.35) 50%, rgba(40,40,40,.35) 50%, rgba(80,80,80,.35) 100%) !important; }\t.de-cfg-tab::" + (nav.Firefox ? "-moz-" : "") + "selection { background: transparent; }\t.de-cfg-unvis { display: none; }\t#de-spell-panel { float: right; }\t#de-spell-panel > a { padding: 0 4px; }\t#de-spell-div { display: table; }\t#de-spell-div > div { display: table-cell; vertical-align: top; }\t#de-spell-edit { padding: 2px !important; width: 323px; height: 194px; max-width: 100%; border: none !important; outline: none !important; }\t#de-spell-rowmeter { padding: 2px 3px 0 0; margin: 2px 0; overflow: hidden; width: 2em; height: 196px; text-align: right; color: #fff; font: 12px courier new; }\t#de-spell-rowmeter:lang(en), #de-spell-rowmeter:lang(fr) { background-color: #616b86; }\t#de-spell-rowmeter:lang(de) { background-color: #777; }" +

	
		"#de-btn-logo { margin-right: 3px; cursor: pointer; }\t#de-panel { height: 25px; z-index: 9999; border-radius: 15px 0 0 0; cursor: default;}\t#de-panel-btns { display: inline-block; padding: 0 0 0 2px; margin: 0; height: 25px; border-left: 1px solid #8fbbed; }\t#de-panel-btns:lang(de), #de-panel-info:lang(de) { border-color: #ccc; }\t#de-panel-btns:lang(fr), #de-panel-info:lang(fr) { border-color: #616b86; }\t#de-panel-btns > li { margin: 0 1px; padding: 0; }\t#de-panel-btns > li, #de-panel-btns > li > a, #de-btn-logo { display: inline-block; width: 25px; height: 25px; }\t#de-panel-btns:lang(en) > li, #de-panel-btns:lang(fr) > li  { transition: all 0.3s ease; }\t#de-panel-btns:lang(en) > li:hover, #de-panel-btns:lang(fr) > li:hover { background-color: rgba(255,255,255,.15); box-shadow: 0 0 3px rgba(143,187,237,.5); }\t#de-panel-btns:lang(de) > li > a { border-radius: 5px; }\t#de-panel-btns:lang(de) > li > a:hover { width: 21px; height: 21px; border: 2px solid #444; }\t#de-panel-info { vertical-align: 6px; padding: " + (nav.Chrome ? 3 : 2) + "px 6px; margin-left: 2px; height: 25px; border-left: 1px solid #8fbbed; color: #fff; font: 18px serif; }" + gif("#de-btn-logo", (p = "R0lGODlhGQAZAIAAAPDw8P///yH5BAEAAAEALAAAAAAZABkA") + "QAI5jI+pywEPWoIIRomz3tN6K30ixZXM+HCgtjpk1rbmTNc0erHvLOt4vvj1KqnD8FQ0HIPCpbIJtB0KADs=") + gif("#de-btn-settings", p + "QAJAjI+pa+API0Mv1Ymz3hYuiQHHFYjcOZmlM3Jkw4aeAn7R/aL6zuu5VpH8aMJaKtZR2ZBEZnMJLM5kIqnP2csUAAA7") + gif("#de-btn-hidden", p + "QAI5jI+pa+CeHmRHgmCp3rxvO3WhMnomUqIXl2UmuLJSNJ/2jed4Tad96JLBbsEXLPbhFRc8lU8HTRQAADs=") + gif("#de-btn-favor", p + "QAIzjI+py+AMjZs02ovzobzb1wDaeIkkwp3dpLEoeMbynJmzG6fYysNh3+IFWbqPb3OkKRUFADs=") + gif("#de-btn-video", p + "AAI9jI+py+0Po5wTWEvN3VjyH20a6HDHB5TiaTIkuyov3MltEuM3nS5z8EPsgsIY6rE6QlA5JDMDbEKn1KqhAAA7") + gif("#de-btn-refresh", p + "AAJBjI+py+0Po5zUgItBxDZrmHUcGAbe15xiybCm5iYegsaHfY8Kvrb6/qPhZr7LgrcyJlHFE1LoVG6ilVewis1qDQUAOw==") + gif("#de-btn-goback", p + "QAIrjI+pmwAMm4u02gud3lzjD4biJgbd6VVPybbua61lGqIoY98ZPcvwD4QUAAA7") + gif("#de-btn-gonext", p + "QAIrjI+pywjQonuy2iuf3lzjD4Zis0Xd6YnQyLbua61tSqJnbXcqHVLwD0QUAAA7") + gif("#de-btn-goup", p + "QAIsjI+pm+DvmDRw2ouzrbq9DmKcBpVfN4ZpyLYuCbgmaK7iydpw1OqZf+O9LgUAOw==") + gif("#de-btn-godown", p + "QAItjI+pu+DA4ps02osznrq9DnZceIxkYILUd7bue6WhrLInLdokHq96tnI5YJoCADs=") + gif("#de-btn-expimg", p + "QAI9jI+pGwDn4GPL2Wep3rxXFEFel42mBE6kcYXqFqYnVc72jTPtS/KNr5OJOJMdq4diAXWvS065NNVwseehAAA7") + gif("#de-btn-preimg", p + "QAJFjI+pGwCcHJPGWdoe3Lz7qh1WFJLXiX4qgrbXVEIYadLLnMX4yve+7ErBYorRjXiEeXagGguZAbWaSdHLOow4j8Hrj1EAADs=") + gif("#de-btn-maskimg", p + "QAJQjI+pGwD3TGxtJgezrKz7DzLYRlKj4qTqmoYuysbtgk02ZCG1Rkk53gvafq+i8QiSxTozIY7IcZJOl9PNBx1de1Sdldeslq7dJ9gsUq6QnwIAOw==") + gif("#de-btn-savethr", p + "QAJFjI+pG+CQnHlwSYYu3rz7RoVipWib+aVUVD3YysAledKZHePpzvecPGnpDkBQEEV03Y7DkRMZ9ECNnemUlZMOQc+iT1EAADs=") + gif("#de-btn-catalog", p + "QAI2jI+pa+DhAHyRNYpltbz7j1Rixo0aCaaJOZ2SxbIwKTMxqub6zuu32wP9WsHPcFMs0XDJ5qEAADs=") + gif("#de-btn-audio-off", p + "QAI7jI+pq+DO1psvQHOj3rxTik1dCIzmSZqfmGXIWlkiB6L2jedhPqOfCitVYolgKcUwyoQuSe3WwzV1kQIAOw==") + gif("#de-btn-audio-on", p + "QAJHjI+pq+AewJHs2WdoZLz7X11WRkEgNoHqimadOG7uAqOm+Y6atvb+D0TgfjHS6RIp8YQ1pbHRfA4n0eSTI7JqP8Wtahr0FAAAOw==") + gif("#de-btn-enable", p + "AAJAjI+py+0Po5wUWKoswOF27z2aMX6bo51lioal2bzwISPyHSZ1lts9fwKKfjQiyXgkslq95TAFnUCdUirnis0eCgA7") + gif("#de-btn-upd-on", "R0lGODlhGQAZAJEAADL/Mv" + (p = "Dw8P///wAAACH5BAEAAAIALAAAAAAZABkAQAJElI+pe2EBoxOTNYmr3bz7OwHiCDzQh6bq06QSCUhcZMCmNrfrzvf+XsF1MpjhCSainBg0AbKkFCJko6g0MSGyftwuowAAOw==")) + gif("#de-btn-upd-off", "R0lGODlhGQAZAJEAAP8yMv" + p) + gif("#de-btn-upd-warn", "R0lGODlhGQAZAJEAAP/0Qf" + p);

		if (Cfg.disabled) {
			$css(x).id = "de-css";
			$css("").id = "de-css-dynamic";
			updateCSS();
			return;
		}

	
		x += "\t.de-post-btns { margin-left: 4px; }\t.de-post-note:not(:empty) { color: inherit; margin: 0 4px; vertical-align: 1px; font: italic bold 12px serif; }\t.de-thread-note { font-style: italic; }\t.de-btn-expthr, .de-btn-fav, .de-btn-fav-sel, .de-btn-hide, .de-btn-hide-user, .de-btn-rep, .de-btn-sage, .de-btn-src, .de-btn-stick, .de-btn-stick-on { transform:rotate(0deg); display: inline-block; margin: 0 4px -2px 0 !important; cursor: pointer; " + (Cfg.postBtnsCSS === 0 ? "color: #4F7942; font-size: 14px; }\t\t.de-post-hide .de-btn-hide:after { content: \"✚\"; }\t\t.de-post-hide .de-btn-hide-user:after { content: \"✚\"; }\t\t.de-btn-expthr:after { content: \"⇕\"; }\t\t.de-btn-fav:after { content: \"★\"; }\t\t.de-btn-fav-sel:after { content: \"[★]\"; }\t\t.de-btn-hide:after { content: \"✖\"; }\t\t.de-btn-hide-user:after { content: \"✖\"; color: red !important; }\t\t.de-btn-rep:after { content: \"▶\"; }\t\t.de-btn-sage:after { content: \"❎\"; }\t\t.de-btn-src:after { content: \"[S]\"; }\t\t.de-btn-stick:after { content: \"◻\"; }\t\t.de-btn-stick-on:after { content: \"◼\"; }" : Cfg.postBtnsCSS === 1 ? "padding: 0 14px 14px 0; }" + gif(".de-post-hide .de-btn-hide", (p = "R0lGODlhDgAOAKIAAPDw8KCgoICAgEtLS////wAAAAAAAAAAACH5BAEAAAQALAAAAAAOAA4AAAM") + "4SLLcqyHKGRe1E1cARPaSwIGVI3bOIAxc26oD7LqwusZcbMcNC9gLHsMHvFFixwFlGRgQdNAoIQEAOw==") + gif(".de-post-hide .de-btn-hide-user", "R0lGODlhDgAOAKIAAP+/v6CgoICAgEtLS////wAAAAAAAAAAACH5BAEAAAQALAAAAAAOAA4AAAM4SLLcqyHKGRe1E1cARPaSwIGVI3bOIAxc26oD7LqwusZcbMcNC9gLHsMHvFFixwFlGRgQdNAoIQEAOw==") + gif(".de-btn-expthr", p + "5SLLcqyHGJaeoAoAr6dQaF3gZGFpO6AzNoLHMAC8uMAty+7ZwbfYzny02qNSKElkloDQSZNAolJAAADs=") + gif(".de-btn-fav", p + "4SLLcqyHGJaeoAoAradec1Wigk5FoOQhDSq7DyrpyvLRpDb84AO++m+YXiVWMAWRlmSTEntAnIQEAOw==") + gif(".de-btn-fav-sel", "R0lGODlhDgAOAKIAAP/hAKCgoICAgEtLS////wAAAAAAAAAAACH5BAEAAAQALAAAAAAOAA4AAAM4SLLcqyHGJaeoAoAradec1Wigk5FoOQhDSq7DyrpyvLRpDb84AO++m+YXiVWMAWRlmSTEntAnIQEAOw==") + gif(".de-btn-hide", p + "7SLLcqyHKGZcUE1ctAPdb0AHeCDpkWi4DM6gtGwtvOg9xDcu0rbc4FiA3lEkGE2QER2kGBgScdColJAAAOw==") + gif(".de-btn-hide-user", "R0lGODlhDgAOAKIAAL//v6CgoICAgEtLS////wAAAAAAAAAAACH5BAEAAAQALAAAAAAOAA4AAAM7SLLcqyHKGZcUE1ctAPdb0AHeCDpkWi4DM6gtGwtvOg9xDcu0rbc4FiA3lEkGE2QER2kGBgScdColJAAAOw==") + gif(".de-btn-rep", p + "2SLLcqyHKGZe0NGABAL5C1XWfM47NsAznqA6qwLbAG8/nfeexvNe91UACywSKxsmAAGs6m4QEADs=") + gif(".de-btn-sage", "R0lGODlhDgAOAJEAAPDw8EtLS////wAAACH5BAEAAAIALAAAAAAOAA4AAAIZVI55pu0AgZs0SoqTzdnu5l1P1ImcwmBCAQA7") + gif(".de-btn-src", p + "/SLLcqyEuKWKYF4Cl6/VCF26UJHaUIzaDMGjA8Gqt7MJ47Naw3O832kxnay1sx11g6KMtBxEZ9DkdEKTYLCEBADs=") + gif(".de-btn-stick", p + "xSLLcqyHKGRe9wVYntQBgKGxMKDJDaQJouqzsMrgDTNO27Apzv88YCjAoGRB8yB4hAQA7") + gif(".de-btn-stick-on", "R0lGODlhDgAOAKIAAL//v6CgoICAgEtLS////wAAAAAAAAAAACH5BAEAAAQALAAAAAAOAA4AAAMxSLLcqyHKGRe9wVYntQBgKGxMKDJDaQJouqzsMrgDTNO27Apzv88YCjAoGRB8yB4hAQA7") : "padding: 0 14px 14px 0; }" + gif(".de-post-hide .de-btn-hide", (p = "R0lGODlhDgAOAJEAAPDw8IyMjP///wAAACH5BAEAAAIALAAAAAAOAA4AAAI") + "ZVI55pu3vAIBI0mOf3LtxDmWUGE7XSTFpAQA7") + gif(".de-post-hide .de-btn-hide-user", "R0lGODlhDgAOAJEAAP+/v4yMjP///wAAACH5BAEAAAIALAAAAAAOAA4AAAIZVI55pu3vAIBI0mOf3LtxDmWUGE7XSTFpAQA7 ") + gif(".de-btn-expthr", p + "bVI55pu0BwEMxzlonlHp331kXxjlYWH4KowkFADs=") + gif(".de-btn-fav", p + "dVI55pu0BwEtxnlgb3ljxrnHP54AgJSGZxT6MJRQAOw==") + gif(".de-btn-fav-sel", "R0lGODlhDgAOAJEAAP/hAIyMjP///wAAACH5BAEAAAIALAAAAAAOAA4AAAIdVI55pu0BwEtxnlgb3ljxrnHP54AgJSGZxT6MJRQAOw==") + gif(".de-btn-hide", p + "dVI55pu2vQJIN2GNpzPdxGHwep01d5pQlyDoMKBQAOw==") + gif(".de-btn-hide-user", "R0lGODlhDgAOAJEAAL//v4yMjP///wAAACH5BAEAAAIALAAAAAAOAA4AAAIdVI55pu2vQJIN2GNpzPdxGHwep01d5pQlyDoMKBQAOw==") + gif(".de-btn-rep", p + "aVI55pu2vAIBISmrty7rx63FbN1LmiTCUUAAAOw==") + gif(".de-btn-sage", "R0lGODlhDgAOAJEAAPDw8FBQUP///wAAACH5BAEAAAIALAAAAAAOAA4AAAIZVI55pu0AgZs0SoqTzdnu5l1P1ImcwmBCAQA7") + gif(".de-btn-src", p + "fVI55pt0ADnRh1uispfvpLkEieGGiZ5IUGmJrw7xCAQA7") + gif(".de-btn-stick", p + "XVI55pu0PI5j00erutJpfj0XiKDKRUAAAOw==") + gif(".de-btn-stick-on", "R0lGODlhDgAOAJEAAL//v4yMjP///wAAACH5BAEAAAIALAAAAAAOAA4AAAIXVI55pu0PI5j00erutJpfj0XiKDKRUAAAOw==")) + (pr.form || pr.oeForm ? "" : ".de-btn-rep { display: none; }") +

	
		cont(".de-src-google", "https://google.com/favicon.ico") + cont(".de-src-yandex", "https://yandex.ru/favicon.ico") + cont(".de-src-tineye", "https://tineye.com/favicon.ico") + cont(".de-src-saucenao", "https://saucenao.com/favicon.ico") + cont(".de-src-iqdb", "//iqdb.org/favicon.ico") +

	
		".de-post-counter:after { counter-increment: de-cnt 1; content: counter(de-cnt); margin-right: 4px; vertical-align: 1px; color: #4f7942; font: bold 11px tahoma; cursor: default; }\t.de-post-deleted:after { content: \"" + Lng.deleted[lang] + "\"; margin-right: 4px; vertical-align: 1px; color: #727579; font: bold 11px tahoma; cursor: default; }" +

	
		"#de-txt-panel { display: block; height: 23px; font-weight: bold; cursor: pointer; }\t#de-txt-panel > span:empty { display: inline-block; width: 27px; height: 23px; }" + gif("#de-btn-bold:empty", (p = "R0lGODlhFwAWAJEAAPDw8GRkZAAAAP///yH5BAEAAAMALAAAAAAXABYAQAJ") + "T3IKpq4YAoZgR0KqqnfzipIUikFWc6ZHBwbQtG4zyonW2Vkb2iYOo8Ps8ZLOV69gYEkU5yQ7YUzqhzmgsOLXWnlRIc9PleX06rnbJ/KITDqTLUAAAOw==") + gif("#de-btn-italic:empty", p + "K3IKpq4YAYxRCSmUhzTfx3z3c9iEHg6JnAJYYSFpvRlXcLNUg3srBmgr+RL0MzxILsYpGzyepfEIjR43t5kResUQmtdpKOIQpQwEAOw==") + gif("#de-btn-under:empty", p + "V3IKpq4YAoRARzAoV3hzoDnoJNlGSWSEHw7JrEHILiVp1NlZXtKe5XiptPrFh4NVKHh9FI5NX60WIJ6ATZoVeaVnf8xSU4r7NMRYcFk6pzYRD2TIUAAA7") + gif("#de-btn-strike:empty", p + "S3IKpq4YAoRBR0qqqnVeD7IUaKHIecjCqmgbiu3jcfCbAjOfTZ0fmVnu8YIHW6lgUDkOkCo7Z8+2AmCiVqHTSgi6pZlrN3nJQ8TISO4cdyJWhAAA7") + gif("#de-btn-spoil:empty", "R0lGODlhFwAWAJEAAPDw8GRkZP///wAAACH5BAEAAAIALAAAAAAXABYAQAJBlIKpq4YAmHwxwYtzVrprXk0LhBziGZiBx44hur4kTIGsZ99fSk+mjrMAd7XerEg7xnpLIVM5JMaiFxc14WBiBQUAOw==") + gif("#de-btn-code:empty", p + "O3IKpq4YAoZgR0KpqnFxokH2iFm7eGCEHw7JrgI6L2F1YotloKek6iIvJAq+WkfgQinjKVLBS45CePSXzt6RaTjHmNjpNNm9aq6p4XBgKADs=") + gif("#de-btn-sup:empty", p + "Q3IKpq4YAgZiSQhGByrzn7YURGFGWhxzMuqqBGC7wRUNkeU7nnWNoMosFXKzi8BHs3EQnDRAHLY2e0BxnWfEJkRdT80NNTrliG3aWcBhZhgIAOw==") + gif("#de-btn-sub:empty", p + "R3IKpq4YAgZiSxquujtOCvIUayAkVZEoRcjCu2wbivMw2WaYi7vVYYqMFYq/i8BEM4ZIrYOmpdD49m2VFd2oiUZTORWcNYT9SpnZrTjiML0MBADs=") + gif("#de-btn-quote:empty", p + "L3IKpq4YAYxRUSKguvRzkDkZfWFlicDCqmgYhuGjVO74zlnQlnL98uwqiHr5ODbDxHSE7Y490wxF90eUkepoysRxrMVaUJBzClaEAADs=") + (nav.Anim ? "@keyframes de-open { 0% { transform: translateY(-100%); } 100% { transform: translateY(0); } }\t\t@keyframes de-close { 0% { transform: translateY(0); } 100% { transform: translateY(-100%); } }\t\t@keyframes de-blink {\t\t\t0%, 100% { transform: translateX(0); }\t\t\t10%, 30%, 50%, 70%, 90% { transform: translateX(-10px); }\t\t\t20%, 40%, 60%, 80% { transform: translateX(10px); }\t\t}\t\t@keyframes de-cfg-open { from { transform: translate(0,50%) scaleY(0); opacity: 0; } }\t\t@keyframes de-cfg-close { to { transform: translate(0,50%) scaleY(0); opacity: 0; } }\t\t@keyframes de-post-open-tl { from { transform: translate(-50%,-50%) scale(0); opacity: 0; } }\t\t@keyframes de-post-open-bl { from { transform: translate(-50%,50%) scale(0); opacity: 0; } }\t\t@keyframes de-post-open-tr { from { transform: translate(50%,-50%) scale(0); opacity: 0; } }\t\t@keyframes de-post-open-br { from { transform: translate(50%,50%) scale(0); opacity: 0; } }\t\t@keyframes de-post-close-tl { to { transform: translate(-50%,-50%) scale(0); opacity: 0; } }\t\t@keyframes de-post-close-bl { to { transform: translate(-50%,50%) scale(0); opacity: 0; } }\t\t@keyframes de-post-close-tr { to { transform: translate(50%,-50%) scale(0); opacity: 0; } }\t\t@keyframes de-post-close-br { to { transform: translate(50%,50%) scale(0); opacity: 0; } }\t\t@keyframes de-post-new { from { transform: translate(0,-50%) scaleY(0); opacity: 0; } }\t\t.de-pview-anim { animation-duration: .2s; animation-timing-function: ease-in-out; animation-fill-mode: both; }\t\t.de-open { animation: de-open .15s ease-out both; }\t\t.de-close { animation: de-close .15s ease-in both; }\t\t.de-blink { animation: de-blink .7s ease-in-out both; }\t\t.de-cfg-open { animation: de-cfg-open .2s ease-out backwards; }\t\t.de-cfg-close { animation: de-cfg-close .2s ease-in both; }\t\t.de-post-new { animation: de-post-new .2s ease-out both; }" : "") +

	
		cont(".de-video-link.de-ytube", "https://youtube.com/favicon.ico") + cont(".de-video-link.de-vimeo", "https://vimeo.com/favicon.ico") + cont(".de-img-arch", "data:image/gif;base64,R0lGODlhEAAQALMAAF82SsxdwQMEP6+zzRA872NmZQesBylPHYBBHP///wAAAAAAAAAAAAAAAAAAAAAAACH5BAEAAAkALAAAAAAQABAAQARTMMlJaxqjiL2L51sGjCOCkGiBGWyLtC0KmPIoqUOg78i+ZwOCUOgpDIW3g3KJWC4t0ElBRqtdMr6AKRsA1qYy3JGgMR4xGpAAoRYkVDDWKx6NRgAAOw==") + cont(".de-img-audio", "data:image/gif;base64,R0lGODlhEAAQAKIAAGya4wFLukKG4oq3802i7Bqy9P///wAAACH5BAEAAAYALAAAAAAQABAAQANBaLrcHsMN4QQYhE01OoCcQIyOYQGooKpV1GwNuAwAa9RkqTPpWqGj0YTSELg0RIYM+TjOkgba0sOaAEbGBW7HTQAAOw==") + ".de-current:after { content: \" ●\"; }\t.de-img-arch, .de-img-audio { color: inherit; text-decoration: none; font-weight: bold; }\t.de-img-pre, .de-img-full { display: block; border: none; outline: none; cursor: pointer; }\t.de-img-pre { max-width: 200px; max-height: 200px; }\t.de-img-full { float: left; }\t.de-img-center { position: fixed; margin: 0 !important; z-index: 9999; background-color: #ccc; border: 1px solid black !important; box-sizing: content-box; -moz-box-sizing: content-box; }\t#de-img-btn-next > div, #de-img-btn-prev > div { height: 36px; width: 36px; }" + gif("#de-img-btn-next > div", "R0lGODlhIAAgAIAAAPDw8P///yH5BAEAAAEALAAAAAAgACAAQAJPjI8JkO1vlpzS0YvzhUdX/nigR2ZgSJ6IqY5Uy5UwJK/l/eI6A9etP1N8grQhUbg5RlLKAJD4DAJ3uCX1isU4s6xZ9PR1iY7j5nZibixgBQA7") + gif("#de-img-btn-prev > div", "R0lGODlhIAAgAIAAAPDw8P///yH5BAEAAAEALAAAAAAgACAAQAJOjI8JkO24ooxPzYvzfJrWf3Rg2JUYVI4qea1g6zZmPLvmDeM6Y4mxU/v1eEKOpziUIA1BW+rXXEVVu6o1dQ1mNcnTckp7In3LAKyMchUAADs=") + "#de-img-btn-next, #de-img-btn-prev { position: fixed; top: 50%; z-index: 10000; margin-top: -8px; background-color: black; cursor: pointer; }\t#de-img-btn-next { right: 0; border-radius: 10px 0 0 10px; }\t#de-img-btn-prev { left: 0; border-radius: 0 10px 10px 0; }\t.de-mp3, .de-video-obj { margin: 5px 20px; white-space: nowrap; clear: both; }\t.de-video-expanded > embed, .de-video-expanded > iframe, .de-video-expanded > a > img { width: 848px; height: 480px; }\t#de-video-list { padding: 0 0 4px; overflow-y: scroll; }\t.de-video-resizer:after { content: \"➕\"; display: inline-block; margin: 0 -15px 0 3px; vertical-align: 8px; color: black; font-size: 12px; cursor: pointer; }\t.de-video-obj > a { display: table; position: relative; border-spacing: 0; }\t.de-video-obj > a:after { opacity: .6; position: absolute; left: 42%; top: 42%; content: url(\"data:image/gif;base64,R0lGODlhPwAsAJEAAAAAAP////8AAP///yH5BAEAAAMALAAAAAA/ACwAAAJvnC2py+0P35kj2ostzbzn44Wig4ymWJ7qt7buC8fyTNf2jee6EAT72ev9RMHgsFMsHjHJ5DLSbD4d0eh0Ua1eeVnrtCu9go1bbresOKPT3jVb6WbA43If/Y7P6/f8vt9V0ZeyN6gHQjhhSFFYRlEAADs=\"); }\t.de-video-obj > a:hover:after { opacity: .85; }\t.de-video-title[de-time]:after { content: \" [\" attr(de-time) \"]\"; color: red; }\ttd > a + .de-video-obj, td > img + .de-video-obj { display: inline-block; }\tvideo { background: black; }\t.de-vocaroo > embed { display: inline-block; }" +

	
		".de-file { display: inline-block; margin: 1px; height: " + (p = aib.multiFile ? 90 : 130) + "px; width: " + p + "px; text-align: center; border: 1px dashed grey; }\t.de-file > .de-file-del, .de-file > .de-file-spoil { float: right; }\t.de-file > .de-file-rar { float: left; }\t.de-file > .de-file-rarmsg { float: left; padding: 0 4px 2px; color: #fff; background-color: rgba(55, 55, 55, 0.5); }\t.de-file > .de-file-utils { display: none; }\t.de-file > div { display: table; width: 100%; height: 100%; cursor: pointer; }\t.de-file > div > div { display: table-cell; vertical-align: middle; }\t.de-file + [type=\"file\"] { opacity: 0; margin: 1px 0 0 -" + (p + 2) + "px !important; vertical-align: top; width: " + (p + 2) + "px !important; height: " + (p + 2) + "px; border: none !important; cursor: pointer; }\t#de-file-area { border-spacing: 0; margin-top: 1px; width: 275px; min-width: 100%; max-width: 100%; overflow-x: auto; overflow-y: hidden; white-space: nowrap; }\t.de-file-drag { background: rgba(88, 88, 88, 0.4); border: 1px solid grey; }\t.de-file-hover > .de-file-utils { display: block !important; position: relative; margin: -18px 2px; }\t.de-file-hover > .de-file-spoil { margin: -16px 21px; }\t.de-file-img > img, .de-file-img > video { max-width: " + (p - 4) + "px; max-height: " + (p - 4) + "px; }\t.de-file-input { max-width: 300px; }\t.de-file-off > div > div:after { content: \"" + Lng.noFile[lang] + "\" }\t.de-file-rarmsg { margin: 0 5px; font: bold 11px tahoma; cursor: default; }\t.de-file-del, .de-file-rar { display: inline-block; margin: 0 4px -3px; width: 16px; height: 16px; cursor: pointer; }\t.de-file-spoil { display: none; }" + gif(".de-file-del", "R0lGODlhEAAQALMOAP8zAMopAJMAAP/M//+DIP8pAP86Av9MDP9sFP9zHv9aC/9gFf9+HJsAAP///wAAACH5BAEAAA4ALAAAAAAQABAAAARU0MlJKw3B4hrGyFP3hQNBjE5nooLJMF/3msIkJAmCeDpeU4LFQkFUCH8VwWHJRHIM0CiIMwBYryhS4XotZDuFLUAg6LLC1l/5imykgW+gU0K22C0RADs=") + gif(".de-file-rar", "R0lGODlhEAAQALMAAF82SsxdwQMEP6+zzRA872NmZQesBylPHYBBHP///wAAAAAAAAAAAAAAAAAAAAAAACH5BAEAAAkALAAAAAAQABAAQARTMMlJaxqjiL2L51sGjCOCkGiBGWyLtC0KmPIoqUOg78i+ZwOCUOgpDIW3g3KJWC4t0ElBRqtdMr6AKRsA1qYy3JGgMR4xGpAAoRYkVDDWKx6NRgAAOw==") +

	
		"#de-txta-resizer { display: inline-block !important; float: none !important; padding: 5px; margin: " + (nav.Presto ? "-2px -10px" : "0 0 -1px -11px") + "; vertical-align: bottom; border-bottom: 2px solid #666; border-right: 2px solid #666; cursor: se-resize; }\t.de-parea { text-align: center; }\t.de-parea-btn-close:after { content: \"" + Lng.hideForm[lang] + "\" }\t.de-parea-btn-thrd:after { content: \"" + Lng.makeThrd[lang] + "\" }\t.de-parea-btn-reply:after { content: \"" + Lng.makeReply[lang] + "\" }\t#de-pform { display: inline-block; vertical-align: middle; }\t#de-pform > form { padding: 0; margin: 0; border: none; }\t#de-pform input[type=\"text\"], #de-pform input[type=\"file\"] { width: 200px; }\t#de-qarea { width: auto !important; min-width: 0; padding: 0 !important; border: none !important; }\t#de-qarea > div:nth-child(2) { text-align: center; }\t.de-qarea-hanging { position: fixed; z-index: 9990; padding: 0 !important; margin: 0 !important; border-radius: 10px 10px 0 0; }\t.de-qarea-hanging > .de-cfg-head { cursor: move; }\t.de-qarea-hanging #de-qarea-utils > span:hover { color: #ff6; }\t.de-qarea-hanging > #de-pform { padding: 2px 2px 0 1px; border: 1px solid gray; }\t.de-qarea-hanging .de-textarea { min-width: 98% !important; resize: none !important; }\t.de-qarea-hanging #de-txta-resizer { display: none !important; }\t.de-qarea-hanging #de-resizer-bottom { position: absolute; margin: -3px; height: 6px; width: 100%; cursor: ns-resize; }\t.de-qarea-hanging #de-resizer-left { position: absolute; margin: -3px; bottom: 3px; top: 3px; width: 6px; cursor: ew-resize; }\t.de-qarea-hanging #de-resizer-right { position: absolute; margin: -3px; bottom: 3px; top: 3px; display: inline-block; width: 6px; cursor: ew-resize; }\t.de-qarea-hanging #de-resizer-top { position: absolute; margin: -3px; height: 6px; width: 100%; cursor: ns-resize; }\t.de-qarea-inline { float: none; clear: left; display: inline-block; width: auto; padding: 3px; margin: 2px 0; }\t#de-qarea-target { font-weight: bold; margin-left: 4px; }\t#de-qarea-utils { float: right; margin-top: " + (nav.Chrome ? -1 : -4) + "px; font: normal 16px arial; cursor: pointer; }\t#de-qarea-utils > span { margin-right: 4px; }\t#de-sagebtn { margin: 4px !important; vertical-align: top; cursor: pointer; }\t.de-textarea { display: inline-block; padding: 3px !important; min-width: 275px !important; min-height: 90px !important; resize: both; transition: none !important; }" +

	
		cont(".de-wait", "data:image/gif;base64,R0lGODlhEAAQALMMAKqooJGOhp2bk7e1rZ2bkre1rJCPhqqon8PBudDOxXd1bISCef///wAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQFAAAMACwAAAAAEAAQAAAET5DJyYyhmAZ7sxQEs1nMsmACGJKmSaVEOLXnK1PuBADepCiMg/DQ+/2GRI8RKOxJfpTCIJNIYArS6aRajWYZCASDa41Ow+Fx2YMWOyfpTAQAIfkEBQAADAAsAAAAABAAEAAABE6QyckEoZgKe7MEQMUxhoEd6FFdQWlOqTq15SlT9VQM3rQsjMKO5/n9hANixgjc9SQ/CgKRUSgw0ynFapVmGYkEg3v1gsPibg8tfk7CnggAIfkEBQAADAAsAAAAABAAEAAABE2QycnOoZjaA/IsRWV1goCBoMiUJTW8A0XMBPZmM4Ug3hQEjN2uZygahDyP0RBMEpmTRCKzWGCkUkq1SsFOFQrG1tr9gsPc3jnco4A9EQAh+QQFAAAMACwAAAAAEAAQAAAETpDJyUqhmFqbJ0LMIA7McWDfF5LmAVApOLUvLFMmlSTdJAiM3a73+wl5HYKSEET2lBSFIhMIYKRSimFriGIZiwWD2/WCw+Jt7xxeU9qZCAAh+QQFAAAMACwAAAAAEAAQAAAETZDJyRCimFqbZ0rVxgwF9n3hSJbeSQ2rCWIkpSjddBzMfee7nQ/XCfJ+OQYAQFksMgQBxumkEKLSCfVpMDCugqyW2w18xZmuwZycdDsRACH5BAUAAAwALAAAAAAQABAAAARNkMnJUqKYWpunUtXGIAj2feFIlt5JrWybkdSydNNQMLaND7pC79YBFnY+HENHMRgyhwPGaQhQotGm00oQMLBSLYPQ9QIASrLAq5x0OxEAIfkEBQAADAAsAAAAABAAEAAABE2QycmUopham+da1cYkCfZ94UiW3kmtbJuRlGF0E4Iwto3rut6tA9wFAjiJjkIgZAYDTLNJgUIpgqyAcTgwCuACJssAdL3gpLmbpLAzEQA7") + ".de-abtn { text-decoration: none !important; outline: none; }\t.de-after-fimg { clear: left; }\t#de-alert { overflow-x: hidden !important; overflow-y: auto !important; -moz-box-sizing: border-box; box-sizing: border-box; max-height: 100vh; position: fixed; right: 0; top: 0; z-index: 9999; font: 14px arial; cursor: default; }\t#de-alert > div { overflow: visible !important; float: right; clear: both; width: auto; min-width: 0pt; padding: 10px; margin: 1px; border: 1px solid grey; white-space: pre-wrap; }\t.de-alert-btn { display: inline-block; vertical-align: top; color: green; cursor: pointer; }\t.de-alert-btn:not(.de-wait) + div { margin-top: .15em; }\t.de-alert-msg { display: inline-block; }\t.de-content textarea { display: block; margin: 2px 0; font: 12px courier new; " + (nav.Presto ? "" : "resize: none !important; ") + "}\t.de-content-block > a { color: inherit; font-weight: bold; font-size: 14px; }\t.de-content-block > input { margin: 0 4px; }\t#de-content-fav, #de-content-hid, #de-content-vid { font-size: 16px; padding: 10px; border: 1px solid gray; border-radius: 8px; }\t.de-editor { display: block; font: 12px courier new; width: 619px; height: 337px; tab-size: 4; -moz-tab-size: 4; -o-tab-size: 4; }\t.de-entry { display: block !important; float: none !important; width: auto; max-width: 100% !important; margin: 2px 0 !important; padding: 0 !important; border: none; font-size: 14px; " + (nav.Presto ? "white-space: nowrap; " : "") + "}\t.de-entry > a { text-decoration: none; border: none; }\t.de-entry > input { margin: 2px 4px; }\t.de-collapse { clear: left; display: block; float: left; margin: 5px 0 10px 0; }\t.de-fav-inf-err { color: #c33; font-size: 12px; }\t.de-fav-inf-new { color: #424f79; }\t.de-fav-inf-new:after { content: \" +\"; }\t.de-fav-inf-old { color: #4f7942; }\t.de-fav-inf-posts { float: right; margin-right: 4px; font: bold 14px serif; cursor: default; }\t.de-fav-title { margin-right: 15px; }\t.de-hidden { float: left; overflow: hidden !important; margin: 0 !important; padding: 0 !important; border: none !important; width: 0 !important; height: 0 !important; display: inline !important; }\t.de-input-key { height: 12px }\t.de-link-hid { text-decoration: line-through !important; }\t.de-link-parent { outline: 1px dotted !important; }\t.de-link-pview { font-weight: bold; }\t.de-link-ref { text-decoration: none; }\t.de-menu { padding: 0 !important; margin: 0 !important; width: auto !important; min-width: 0; z-index: 9999; border: 1px solid grey !important;}\t.de-menu-item { display: block; padding: 3px 10px; color: inherit; text-decoration: none; font: 13px arial; white-space: nowrap; cursor: pointer; }\t.de-menu-item:hover { background-color: #222; color: #fff; }\t.de-new-post { " + (nav.Presto ? "border-left: 4px solid blue; border-right: 4px solid blue; }" : "box-shadow: 6px 0 2px -2px blue, -6px 0 2px -2px blue; }") + "\t.de-omitted { color: grey; font-style: italic; }\t.de-omitted:before { content: \"" + Lng.postsOmitted[lang] + "\"; }\t.de-post-hide > " + aib.qHide + " { display: none !important; }\t.de-pview { position: absolute; width: auto; min-width: 0; z-index: 9999; border: 1px solid grey !important; margin: 0 !important; display: block !important; }\t.de-pview-info { padding: 3px 6px !important; }\t.de-ref-op:after { content: \" [OP]\"; }\t.de-ref-del:after { content: \" [del]\"; }\t.de-refmap { margin: 10px 4px 4px 4px; font-size: 75%; font-style: italic; }\t.de-refmap:before { content: \"" + Lng.replies[lang] + " \"; }\t.de-refcomma:last-child { display: none; }\t.de-selected, .de-error-key { " + (nav.Presto ? "border-left: 4px solid red; border-right: 4px solid red; }" : "box-shadow: 6px 0 2px -2px red, -6px 0 2px -2px red; }") + "\t#de-updater-btn:after { content: \"" + Lng.getNewPosts[lang] + "\" }\t#de-updater-count:before { content: \": \" }\t#de-updater-div { clear: left; margin-top: 10px; }\t.de-viewed { color: #888 !important; }\tform > hr { clear: both }";

		if (!nav.Firefox) {
			x = x.replace(/(transition|keyframes|transform|animation|linear-gradient)/g, nav.cssFix + "$1");
			if (!nav.Presto) {
				x = x.replace(/\(to bottom/g, "(top").replace(/\(to top/g, "(bottom");
			}
		}
		$css(x).id = "de-css";
		$css("").id = "de-css-dynamic";
		$css("").id = "de-css-user";
		updateCSS();
	}

	function updateCSS() {
		var x = "";
		if (Cfg.attachPanel) {
			x += ".de-content { position: fixed; right: 0; bottom: 25px; z-index: 9999; max-height: 95%; overflow-x: visible; overflow-y: auto; }\t\t#de-content-fav, #de-content-hid { overflow-y: scroll; }\t\t#de-panel { position: fixed; right: 0; bottom: 0; }";
		} else {
			x += ".de-content { clear: both; float: right; }\t\t#de-panel { float: right; clear: both; }";
		}
		if (Cfg.disabled) {
			$id("de-css-dynamic").textContent = x;
			return;
		}
		if (Cfg.maskImgs) {
			x += ".de-img-pre, .de-video-obj, .thumb, .ca_thumb, .fileThumb, img[src*=\"spoiler\"], img[src*=\"thumb\"], img[src^=\"blob\"] { opacity: 0.07 !important; }\t\t\t.de-img-pre:hover, .de-video-obj:hover, .thumb:hover, .ca_thumb:hover, .fileThumb:hover, img[src*=\"spoiler\"]:hover, img[src*=\"thumb\"]:hover, img[src^=\"blob\"]:hover { opacity: 1 !important; }";
		}
		if (Cfg.delImgNames) {
			x += ".de-img-name { text-transform: capitalize; text-decoration: none; }";
		}
		if (!aib.dobr && !aib.krau && !aib.mak) {
			x += ".de-img-full { margin: 2px 5px; }";
		}
		if (Cfg.noSpoilers) {
			if (aib.krau || aib.fch || aib._8ch || aib._410 || aib.dio) {
				x += ".spoiler, s { color: #fff !important; }\t\t\t\t.spoiler > a, s > a:not(:hover) { color: inherit !important; }";
			} else {
				x += ".spoiler { color: inherit !important; }\t\t\t\t.spoiler > a { color: inherit !important; }";
			}
		}
		if (Cfg.widePosts) {
			x += "." + aib.cReply.replace(/\s/, ".") + ":not(.de-pview) { float: none; width: 100%; }";
		}
		x += ".postarea, .recaptcha_image_cell + td, .recaptcha_image_cell + td + td, small[id^=\"rfmap\"], .theader, .thumbnailmsg, " + (Cfg.panelCounter ? "" : "#de-panel-info, ") + (Cfg.imgNavBtns ? "" : "#de-img-btn-next, #de-img-btn-prev, ") + (Cfg.showHideBtn ? "" : ".de-btn-hide, ") + (Cfg.showRepBtn ? "" : ".de-btn-rep, ") + (Cfg.removeHidd ? ".de-link-ref.de-link-hid, .de-link-ref.de-link-hid + .de-refcomma, " : "") + (Cfg.delHiddPost ? ".de-thr-hid, .de-thr-hid + div + hr, .de-thr-hid + div + br, .de-thr-hid + div + br + hr, " : "") + (Cfg.noPostNames ? aib.qName + ", ." + aib.cTrip + ", " : "") + (Cfg.noBoardRule ? (aib.mak ? ".rules-area" : aib.krau ? "#rules_row" : aib.futa ? ".chui" : ".rules, #rules") + ", " : "") + (!aib.kus && (aib.multiFile || !Cfg.fileThumb) ? "#de-pform form > table > tbody > tr > td:not([colspan]):first-child, #de-pform form > table > tbody > tr > th:first-child, " : "") + "body > hr { display: none !important; }";
		$id("de-css-dynamic").textContent = x + "\n" + aib.css + "\n" + aib.cssEn;
		$id("de-css-user").textContent = Cfg.userCSS ? Cfg.userCSSTxt : "";
	}




	function addDelformStuff() {
		preloadImages(null);
		new Logger().log("Preload images");
		embedMediaLinks(null);
		new Logger().log("Audio links");
		if (Cfg.addYouTube) {
			new VideosParser().parse(null).end();
			new Logger().log("Video links");
		}
		if (Cfg.addImgs) {
			embedImagesLinks(dForm.el);
			new Logger().log("Image-links");
		}
		processImageNames(dForm.el);
		new Logger().log("Image names");
		if (dForm.firstThr && Cfg.linksNavig === 2) {
			genRefMap(pByNum, "");
			for (var post = dForm.firstThr.op; post; post = post.next) {
				if (post.hasRef) {
					addRefMap(post, "");
				}
			}
			new Logger().log("Reflinks map");
		}
	}

	if (/^(?:about|chrome|opera|res):$/i.test(window.location.protocol)) {
		return;
	}
	switch (window.name) {
		case "":
			break;
		case "de-iframe-pform":
		case "de-iframe-dform":
			$script("window.top.postMessage(\"" + window.name + "\" + document.documentElement.outerHTML, \"*\");");
			return;
	}

	if (doc.readyState === "interactive" || doc.readyState === "complete") {
		needScroll = false;
		async(initScript)(true);
	} else {
		aib = getImageBoard(true, false);
		if (aib) {
			if (!checkStorage()) {
				return;
			}
			if (aib.earlyInit) {
				aib.earlyInit();
			}
			initNavFuncs();
			async(readCfg)();
		}
		needScroll = true;
		doc.addEventListener(doc.onmousewheel !== undefined ? "mousewheel" : "DOMMouseScroll", function wheelFunc(e) {
			needScroll = false;
			doc.removeEventListener(e.type, wheelFunc, false);
		}, false);
		doc.addEventListener("DOMContentLoaded", async(initScript.bind(null, false)), false);
	}
})(window.opera && window.opera.scriptStorage, window.FormData);

                     })();