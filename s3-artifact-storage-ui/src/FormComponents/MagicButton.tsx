import {React} from '@jetbrains/teamcity-api';
import Button from '@jetbrains/ring-ui/components/button/button';
import magicWandIcon from '@jetbrains/icons/magic-wand';

type OwnProps = {
  title?: string,
  onClick: () => Promise<void>
}

export default function MagicButton({title, onClick}: OwnProps) {
  return (
    <Button
      title={title}
      icon={magicWandIcon}
      primary
      onClick={onClick}
    />
  );
}
