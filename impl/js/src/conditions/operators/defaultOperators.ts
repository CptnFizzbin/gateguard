import {EqOperator} from "./equality/eqOperator";
import {NeOperator} from "./equality/neOperator";
import {GtOperator} from "./numeric/gtOperator";
import {GteOperator} from "./numeric/gteOperator";
import {LtOperator} from "./numeric/ltOperator";
import {LteOperator} from "./numeric/lteOperator";
import {InOperator} from "./collection/inOperator";
import {HasOperator} from "./collection/hasOperator";
import {SubstrOperator} from "./string/substrOperator";
import {OrOperator} from "./logical/orOperator";
import {AndOperator} from "./logical/andOperator";
import {NotOperator} from "./logical/notOperator";
import {FieldOperator} from "./field/fieldOperator";

/** Every operator {@link ConditionResolver} understands natively (SPEC_V1-0-0.md §7.4.1-§7.4.11). */
export const DefaultOperators = [
  EqOperator,
  NeOperator,
  GtOperator,
  GteOperator,
  LtOperator,
  LteOperator,
  InOperator,
  HasOperator,
  SubstrOperator,
  OrOperator,
  AndOperator,
  NotOperator,
  FieldOperator,
]
